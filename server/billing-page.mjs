export const billingPage = (nonce, csrf) => `<!doctype html>
<html lang="ru"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>TangledLink · Продление</title><style>
:root{font-family:system-ui,sans-serif;background:#050806;color:#eff6f1}body{margin:0;padding:24px 16px}
main{max-width:1000px;margin:auto}a{color:#7cfc8a}.card{background:#0b1210;border:1px solid #304238;border-radius:24px;padding:20px;margin:20px 0}
p{line-height:1.6;color:#95a89e}label{display:block;margin:12px 0}input{box-sizing:border-box;max-width:100%;padding:12px;background:#0f1915;color:#eff6f1;border:1px solid #304238;border-radius:12px;font:inherit}
button{cursor:pointer;border:0;border-radius:24px;padding:12px 20px;font:inherit;font-weight:600;background:linear-gradient(115deg,#c3ff92,#6ef5a8,#21d4a4);color:#04140c}button:disabled{opacity:.5;cursor:wait}
table{width:100%;border-collapse:collapse}td,th{text-align:left;padding:12px 8px;border-bottom:1px solid #304238}#preview{white-space:pre-line}
:focus-visible{outline:3px solid #7cfc8a;outline-offset:3px}
</style></head><body><main><a href="/">← Аккаунты</a><h1>Оплаты и продления</h1>
<p>150 ₽ за 30 дней. Пользователь открывает страницу Choco из приложения и указывает свой логин в поле «Имя». Сверьте имя и поступление 150 ₽ с заказом, затем подтвердите оплату. Переход на сайт не подтверждает платёж.</p>
<section class="card"><h2>Создать заказ</h2><form id="create"><label>ID аккаунта (точный email в 3x-ui)<br><input id="account" required maxlength="128" pattern="[A-Za-z0-9_-]+"></label><button>Создать заказ</button></form></section>
<p id="message" role="status" aria-live="polite"></p>
<section class="card"><h2>Последние заказы</h2><button id="refresh">Обновить</button><div style="overflow:auto"><table><thead><tr><th>Заказ / ID</th><th>Сумма</th><th>Состояние</th><th>Действия</th></tr></thead><tbody id="orders"></tbody></table></div></section>
<section class="card" id="confirmation" hidden><h2>Проверка и продление</h2><p id="preview"></p>
<form id="confirm"><button id="confirmButton">Подтвердить оплату и продлить</button></form></section>
</main><script nonce="${nonce}">
const csrf=${JSON.stringify(csrf)};
let busy=false, selected=null;
const message=document.getElementById('message');
const date=v=>new Date(v).toLocaleString();
async function api(path,payload) {
  const r=await fetch(path,{method:payload?'POST':'GET',credentials:'same-origin',cache:'no-store',
    headers:payload?{'Content-Type':'application/json','X-CSRF-Token':csrf}:{},body:payload?JSON.stringify(payload):undefined});
  const data=await r.json();if(!r.ok)throw new Error(data.error||'server_error');return data;
}
async function work(fn) {
  if(busy)return;busy=true;document.querySelectorAll('button').forEach(b=>b.disabled=true);
  message.textContent='Выполняется…';
  try{await fn();}catch(e){
    const errors={
      paid_order_cannot_reset:'Подтверждённый заказ нельзя сбросить.',
      confirmation_in_progress:'Заказ сейчас обрабатывается. Дождитесь завершения и обновите список.',
      order_reset_requires_review:'Срок или клиент в 3x-ui уже изменился. Сброс остановлен. Проверьте панель; для завершения исходной попытки повторите подтверждение.',
      panel_unavailable:'Не удалось проверить 3x-ui. Заказ не сброшен. Повторите позже.'
    };
    message.textContent='Ошибка: '+(errors[e.message]||e.message);
  }
  finally{busy=false;document.querySelectorAll('button').forEach(b=>b.disabled=false);}
}
async function refresh() {
  const data=await api('/api/billing/orders'),target=document.getElementById('orders');target.replaceChildren();
  for(const o of data.orders) {
    const tr=document.createElement('tr');
    for(const value of [o.id+' / '+o.account, '150 ₽', o.status+(o.pendingSync?' · узлы синхронизируются':'')+(o.error?' · '+o.error:'')]) {
      const td=document.createElement('td');td.textContent=value;tr.append(td);
    }
    const td=document.createElement('td'),b=document.createElement('button');b.textContent='Проверить';
    b.addEventListener('click',()=>work(async()=>{
      selected=null;document.getElementById('confirmation').hidden=true;
      const data=await api('/api/billing/preview',{orderId:o.id});selected=o.id;
      document.getElementById('preview').textContent='Заказ: '+o.id+'\\nАккаунт / email: '+o.account+'\\nТекущий срок: '+date(data.currentExpiry)+'\\nПосле подтверждения: '+date(data.targetExpiry)+'\\nПодключений: '+data.inboundCount+'\\nЛимит и расход трафика сохраняются.';
      document.getElementById('confirmation').hidden=data.order.status==='paid';
      message.textContent=data.order.status==='paid'?'Этот заказ уже подтверждён.':'Проверьте платёж перед подтверждением.';
    }));td.append(b);
    if(o.status!=='paid'){
      const reset=document.createElement('button');reset.type='button';reset.textContent='Сбросить заказ';reset.style.margin='8px 0';
      reset.addEventListener('click',()=>{
        if(busy||!confirm('Сбросить заказ '+o.id+' для '+o.account+' в ожидание оплаты? Срок подписки не изменится.'))return;
        work(async()=>{
          await api('/api/billing/reset',{orderId:o.id});
          selected=null;document.getElementById('confirmation').hidden=true;
          await refresh();message.textContent='Заказ сброшен. В приложении нажмите «Проверить статус», чтобы снова появилась кнопка оплаты.';
        });
      });td.append(reset);
    }
    tr.append(td);target.append(tr);
  }
}
document.getElementById('refresh').addEventListener('click',()=>work(async()=>{await refresh();message.textContent='Список обновлён.';}));
document.getElementById('create').addEventListener('submit',e=>{e.preventDefault();work(async()=>{
  const data=await api('/api/billing/create',{id:document.getElementById('account').value.trim()});
  await refresh();message.textContent='Заказ: '+data.order.id;
});});
document.getElementById('confirm').addEventListener('submit',e=>{e.preventDefault();if(!selected)return;
  if(!confirm('Продлить заказ '+selected+' после проверенного поступления 150 ₽?'))return;
  work(async()=>{const data=await api('/api/billing/confirm',{orderId:selected});
    document.getElementById('confirmation').hidden=true;selected=null;await refresh();
    message.textContent=data.order.pendingSync?'Панель приняла продление; проверьте синхронизацию узлов в 3x-ui.':'Оплата подтверждена. Подписка продлена до '+date(data.order.expiresAt)+'.';
  });
});
work(async()=>{await refresh();message.textContent='';});
</script></body></html>`;
