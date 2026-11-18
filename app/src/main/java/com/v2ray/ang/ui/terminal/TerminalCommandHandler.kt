package com.v2ray.ang.ui.terminal
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.*
import com.v2ray.ang.ui.main.*

class ConnectionRequestGate {
 private val target = MutableStateFlow<Boolean?>(null)
 val targetState: StateFlow<Boolean?> = target.asStateFlow()
 val pending get() = target.value != null
 fun acquire(running:Boolean):Boolean = target.compareAndSet(null,!running)
 fun observed(running:Boolean) {target.compareAndSet(running,null)}
 fun finished() {target.value=null}
}
data class TerminalState(val page:String="console",val input:String="",val lines:List<String> = emptyList(),
 val history:List<String> = emptyList(),val cursor:Int=0,val events:List<String> = emptyList(),val saving:Boolean=false)
internal class TerminalCommandHandler(private val scope:CoroutineScope, private val store:AppearanceStore,
 private val snapshot:()->MainUiState, private val rows:()->List<ServerRowUiModel>,
 private val pending:()->Boolean, private val label:(Int)->String,private val device:String,private val version:String) {
 private val mutable=MutableStateFlow(TerminalState())
 val state=mutable.asStateFlow()
 private var lastRunning:Boolean?=null
 val whitelist=listOf("help","connect","disconnect","status","servers","server select ","stats","ping","clear","theme","theme list","theme set matrix_green","theme set catppuccin_mocha","theme set tokyo_night","theme set gruvbox_dark","neofetch","fastfetch","about","sudo tangledlink connect","sudo tangledlink disconnect","sudo pacman -S freedom")
 fun input(v:String){mutable.update{it.copy(input=v.take(257))}}
 fun open(page:String){
  val entering=page=="events"&&state.value.page!="events"
  mutable.update{it.copy(page=page,events=if(entering)emptyList()else it.events)}
  if(entering){lastRunning=null;observe(snapshot().isRunning)}
 }
 fun observe(running:Boolean){
   if(state.value.page!="events"||lastRunning==running)return
   val line=label(if(lastRunning==null)R.string.terminal_snapshot else R.string.terminal_transition)+": "+label(if(running)R.string.terminal_connected else R.string.terminal_disconnected)
   lastRunning=running
   mutable.update{it.copy(events=(it.events+line).takeLast(100))}
 }
 fun history(delta:Int){mutable.update{val i=(it.cursor+delta).coerceIn(0,it.history.size);it.copy(cursor=i,input=it.history.getOrNull(i)?:"")}}
 fun suggestions():List<String> = (whitelist+rows().map{"server select "+it.guid}).filter{it.startsWith(state.value.input,true)}.take(6)
 fun complete(){suggestions().firstOrNull()?.let(::input)}
 private fun out(line:String){mutable.update{it.copy(lines=(it.lines+line.lineSequence().map{v->v.take(256)}.take(400).toList()).takeLast(400))}}
 private fun error(id:Int)=out("[ERROR] "+label(id))
 fun diagnostics():String=listOf(label(R.string.terminal_os)+": Android",label(R.string.terminal_device)+": "+device,label(R.string.terminal_app)+": TangledLink "+version,
 label(R.string.terminal_service)+": "+label(if(snapshot().isRunning)R.string.terminal_connected else R.string.terminal_disconnected),
 label(R.string.terminal_server_id)+": "+(snapshot().selectedGuid?:label(R.string.terminal_no_data)),
 label(R.string.terminal_protocol)+": "+(rows().firstOrNull{it.guid==snapshot().selectedGuid}?.profile?.configType?.name?:label(R.string.terminal_no_data)),
 label(R.string.terminal_latency)+": "+(rows().firstOrNull{it.guid==snapshot().selectedGuid}?.testDelayMillis?.takeIf{it>0}?.let{it.toString()+" ms"}?:label(R.string.terminal_no_data)),
 label(R.string.terminal_uptime)+": "+(snapshot().traffic.latest?.connectedMillis?.div(1000)?.toString()?:label(R.string.terminal_no_data))).joinToString("\n")
 fun execute(dispatch:(MainAction)->Unit){
   val parsed=TerminalCommandParser.parse(state.value.input); input("")
   if(parsed is TerminalParseResult.Empty)return
   if(parsed is TerminalParseResult.Invalid){error(when(parsed.error){
    TerminalInputError.TOO_LONG->R.string.terminal_input_too_long
    TerminalInputError.UNSUPPORTED_SYNTAX->R.string.terminal_input_unsupported_syntax
    TerminalInputError.UNKNOWN_COMMAND->R.string.terminal_input_unknown_command
    TerminalInputError.INVALID_ARGUMENTS->R.string.terminal_input_invalid_arguments
    TerminalInputError.UNKNOWN_PALETTE->R.string.terminal_input_unknown_palette
   });return}
   val c=(parsed as TerminalParseResult.Parsed).command
   if(c is TerminalCommand.SelectServer&&rows().none{it.guid==c.guid}){error(R.string.terminal_server_missing);return}
   val canonical=when(c){
    is TerminalCommand.SelectServer->"server select "+c.guid
    is TerminalCommand.SetPalette->"theme set "+c.palette.stableId
    TerminalCommand.Freedom->"sudo pacman -S freedom"
    TerminalCommand.ShowPalette->"theme"
    TerminalCommand.ListPalettes->"theme list"
    else->c.toString().substringAfterLast('.').lowercase(java.util.Locale.ROOT)
   }
   mutable.update{val h=(it.history+canonical).takeLast(50);it.copy(history=h,cursor=h.size)}
   when(c){
    TerminalCommand.Connect,TerminalCommand.Disconnect->when(terminalConnectionIntent(c,snapshot().isRunning,pending())){
      TerminalConnectionIntent.BUSY->error(R.string.terminal_busy)
      TerminalConnectionIntent.ALREADY_IN_STATE->out(label(if(snapshot().isRunning)R.string.terminal_already_running else R.string.terminal_already_stopped))
      TerminalConnectionIntent.REQUEST_CONNECT,TerminalConnectionIntent.REQUEST_DISCONNECT->dispatch(MainAction.ToggleService)
      else->Unit
    }
    TerminalCommand.Help->out(whitelist.joinToString("\n"))
    TerminalCommand.Status->out(label(if(snapshot().isRunning)R.string.terminal_connected else R.string.terminal_disconnected))
    TerminalCommand.Servers->if(rows().isEmpty())out(label(R.string.terminal_no_data)) else rows().forEach{out(it.guid)}
    is TerminalCommand.SelectServer->dispatch(MainAction.SelectServer(c.guid))
    TerminalCommand.Ping->if(snapshot().selectedGuid==null)error(R.string.terminal_server_missing)else if(!snapshot().isRunning)error(R.string.terminal_unavailable)else dispatch(MainAction.TestCurrentServer)
    TerminalCommand.Clear->mutable.update{it.copy(lines=emptyList())}
    TerminalCommand.Stats->out(snapshot().traffic.latest?.let{"↓ "+it.download.toLong().toString()+" B/s ↑ "+it.upload.toLong().toString()+" B/s"+"\n"+it.connectedMillis/1000+" s"}?:label(R.string.terminal_no_data))
    TerminalCommand.ShowPalette->out(store.appearance.value.terminalPalette.stableId)
    TerminalCommand.ListPalettes->out(TerminalPaletteId.entries.joinToString("\n"){it.stableId})
    is TerminalCommand.SetPalette->{
      if(state.value.saving){error(R.string.terminal_busy);return}
      mutable.update{it.copy(saving=true)}
      scope.launch{
        val ok=withContext(Dispatchers.IO){runCatching{store.saveAppearance(store.appearance.value.copy(terminalPalette=c.palette))}.getOrDefault(false)}
        mutable.update{it.copy(saving=false)}
        if(ok)out(c.palette.stableId)else error(R.string.theme_save_error)
      }
    }
    TerminalCommand.Neofetch->open("fetch")
    TerminalCommand.About->out("TangledLink "+version)
    TerminalCommand.Freedom->out(label(R.string.terminal_freedom))
   }
 }
}
