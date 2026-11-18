#!/usr/bin/env python3
"""Render repeatable 12-second animations using existing scene art, Cairo and FFmpeg.

Python standard library only; system libcairo and FFmpeg with libx264 are required.
This is deterministic motion compositing, not image-to-video model generation.
"""
from pathlib import Path
from math import sin, cos, pi, floor
import ctypes as C
import ctypes.util
import subprocess
import argparse

PACK=Path(__file__).resolve().parents[1]
WORLDS=('original','sakura','aurora','cyberpunk')
COLORS={'original':('#7CFC8A','#48F0A9'),'sakura':('#AD75FF','#F28BC8'),'aurora':('#49F2C2','#56B8FF'),'cyberpunk':('#FF3DB8','#45D9FF')}
LIB=C.CDLL(ctypes.util.find_library('cairo'))
PTR=C.c_void_p; D=C.c_double; I=C.c_int
def bind(name,result,*args):
 f=getattr(LIB,'cairo_'+name); f.restype=result; f.argtypes=list(args); return f
surface_create=bind('image_surface_create',PTR,I,I,I)
surface_png=bind('image_surface_create_from_png',PTR,C.c_char_p)
surface_status=bind('surface_status',I,PTR)
surface_width=bind('image_surface_get_width',I,PTR)
surface_height=bind('image_surface_get_height',I,PTR)
surface_data=bind('image_surface_get_data',C.POINTER(C.c_ubyte),PTR)
surface_stride=bind('image_surface_get_stride',I,PTR)
surface_flush=bind('surface_flush',None,PTR)
surface_write=bind('surface_write_to_png',I,PTR,C.c_char_p)
surface_destroy=bind('surface_destroy',None,PTR)
create=bind('create',PTR,PTR)
destroy=bind('destroy',None,PTR)
save=bind('save',None,PTR); restore=bind('restore',None,PTR)
translate=bind('translate',None,PTR,D,D); scale=bind('scale',None,PTR,D,D); rotate=bind('rotate',None,PTR,D)
source_surface=bind('set_source_surface',None,PTR,PTR,D,D)
source_rgba=bind('set_source_rgba',None,PTR,D,D,D,D)
paint=bind('paint',None,PTR); paint_alpha=bind('paint_with_alpha',None,PTR,D)
move=bind('move_to',None,PTR,D,D); line=bind('line_to',None,PTR,D,D)
curve=bind('curve_to',None,PTR,D,D,D,D,D,D)
close=bind('close_path',None,PTR); fill=bind('fill',None,PTR); stroke=bind('stroke',None,PTR)
width=bind('set_line_width',None,PTR,D); arc=bind('arc',None,PTR,D,D,D,D,D)
new_path=bind('new_path',None,PTR); rectangle=bind('rectangle',None,PTR,D,D,D,D); clip=bind('clip',None,PTR)
line_cap=bind('set_line_cap',None,PTR,I)
pattern_radial=bind('pattern_create_radial',PTR,D,D,D,D,D,D)
pattern_linear=bind('pattern_create_linear',PTR,D,D,D,D)
pattern_stop=bind('pattern_add_color_stop_rgba',None,PTR,D,D,D,D,D)
pattern_destroy=bind('pattern_destroy',None,PTR)
source=bind('set_source',None,PTR,PTR)
font_face=bind('select_font_face',None,PTR,C.c_char_p,I,I)
font_size=bind('set_font_size',None,PTR,D)
show_text=bind('show_text',None,PTR,C.c_char_p)

def rgb(value): return tuple(int(value[i:i+2],16)/255 for i in (1,3,5))
def color(cr,value,a=1): source_rgba(cr,*rgb(value),a)
def rand(i): return (sin(i*127.1+311.7)*43758.5453)%1
def fract(v): return v-floor(v)
def glow(cr,x,y,r,value,alpha):
 p=pattern_radial(x,y,0,x,y,r)
 pattern_stop(p,0,*rgb(value),alpha)
 pattern_stop(p,.35,*rgb(value),alpha*.48)
 pattern_stop(p,1,*rgb(value),0)
 source(cr,p); new_path(cr); arc(cr,x,y,r,0,2*pi); fill(cr); pattern_destroy(p)

class Renderer:
 def __init__(self):
  self.art={}
  for world in WORLDS:
   p=PACK/f'sources/base/tngl_bg_{world}.png'
   s=surface_png(str(p).encode()); assert surface_status(s)==0,p
   self.art[world]=(s,surface_width(s),surface_height(s))
 def release(self):
  for s,_,_ in self.art.values(): surface_destroy(s)
 def scene(self,cr,world,p,w,h):
  p=p%1; q=2*pi*p; s,sw,sh=self.art[world]
  save(cr); rectangle(cr,0,0,w,h); clip(cr)
  # Small periodic camera drift. Overscan prevents exposing empty pixels.
  zoom=1.014+.004*sin(q)
  k=max(w/sw,h/sh)*zoom
  translate(cr,(w-sw*k)/2+1.4*sin(q)*w/720,(h-sh*k)/2+1.8*sin(q)*h/1600)
  scale(cr,k,k); source_surface(cr,s,0,0); paint(cr); restore(cr)
  save(cr); rectangle(cr,0,0,w,h); clip(cr)
  primary,secondary=COLORS[world]
  if world=='original':
   glow(cr,.88*w,.10*h,.40*w,primary,.17+.07*sin(q))
   glow(cr,.14*w,.61*h,.42*w,secondary,.13+.06*cos(q))
   for i in range(18):
    u=fract(p+rand(i+2)); x=(.05+.18*rand(i+101) if i%2==0 else .78+.18*rand(i+101))*w
    x+=.015*w*sin(q+i); y=(.04+.88*u)*h
    a=.32*(sin(pi*u)**2); glow(cr,x,y,w*.008,primary,a)
  elif world=='sakura':
   glow(cr,.86*w,.08*h,.24*w,primary,.08+.035*sin(q))
   for i in range(12):
    u=fract(p+rand(i+8)); x=(rand(i+51)+.035*sin(q+i*.73))*w
    y=(-.08+1.16*u)*h; a=.52*min(1,u*8,(1-u)*8)
    r=w*(.009+.007*rand(i+100)); angle=q*(1+i%2)+i
    save(cr); translate(cr,x,y); rotate(cr,angle); scale(cr,r,r)
    color(cr,'#F2A5DA',a)
    move(cr,-.75,0); curve(cr,-.45,-.8,.6,-.7,.85,0); curve(cr,.45,.65,-.4,.8,-.75,0); fill(cr)
    color(cr,'#FFE2F4',a*.55); width(cr,.08); move(cr,-.5,0); line(cr,.5,0); stroke(cr); restore(cr)
  elif world=='aurora':
   glow(cr,.05*w,.19*h,.75*w,primary,.11+.06*sin(q))
   glow(cr,.94*w,.13*h,.70*w,secondary,.10+.05*cos(q))
   for side in (0,1):
    for band in range(3):
     x=(.015+.065*band if side==0 else .985-.065*band)*w
     bend=.018*w*sin(q+band*.9)
     grad=pattern_linear(x-.065*w,0,x+.065*w,0)
     pattern_stop(grad,0,*rgb(primary),0)
     pattern_stop(grad,.5,*rgb(primary),.11+.035*sin(q+band))
     pattern_stop(grad,1,*rgb(secondary),0)
     source(cr,grad); move(cr,x-.065*w,-.08*h)
     curve(cr,x+.08*w+bend,.08*h,x-.05*w+bend,.22*h,x+.03*w,.35*h)
     line(cr,x+.08*w,.36*h)
     curve(cr,x+.01*w+bend,.22*h,x+.14*w+bend,.08*h,x+.065*w,-.08*h)
     close(cr); fill(cr); pattern_destroy(grad)
   for i in range(7):
    yy=(.72+i*.013+.003*sin(q+i))*h
    glow(cr,(.11 if i%2==0 else .88)*w,yy,.14*w,primary,.018+.012*sin(q+i))
  else:
   glow(cr,.11*w,.80*h,.32*w,primary,.13+.065*sin(q))
   glow(cr,.91*w,.72*h,.28*w,secondary,.12+.055*cos(q))
   line_cap(cr,1)
   for i in range(56):
    side=i%3
    x=(.025+.23*rand(i+91) if side==0 else .75+.23*rand(i+91) if side==1 else .25+.5*rand(i+91))*w
    u=fract(4*p+rand(i+60)); y=(-.05+1.10*u)*h
    a=(.30 if side<2 else .07)*min(1,u*12,(1-u)*12)
    length=h*(.01+.013*rand(i+300)); color(cr,secondary if i%2 else '#F49ED5',a)
    width(cr,w*(.0008+.0006*rand(i+444))); move(cr,x,y); line(cr,x-.003*w,y+length); stroke(cr)
  restore(cr)
 def portal(self,cr,world,p,cx,cy,r):
  t=(p%1)*12; primary,secondary=COLORS[world]
  state='CONNECTING' if 3<=t<6 else 'CONNECTED' if 6<=t<10 else 'DISCONNECTED'
  power=1 if state=='CONNECTED' else .55 if state=='CONNECTING' else .25
  glow(cr,cx,cy,r*1.38,primary,.26*power)
  new_path(cr); arc(cr,cx,cy,r*.92,0,2*pi); color(cr,'#08101A',.90); fill(cr)
  width(cr,r*.032); color(cr,primary,.18+power*.22); new_path(cr); arc(cr,cx,cy,r,0,2*pi); stroke(cr)
  if state=='CONNECTING':
   start=2*pi*(6*p%1)-pi/2
   new_path(cr); arc(cr,cx,cy,r,start,start+pi*1.45); color(cr,primary,.94); stroke(cr)
   new_path(cr); arc(cr,cx,cy,r*.91,-start,-start+pi*.5); color(cr,secondary,.5); width(cr,r*.016); stroke(cr)
  elif state=='CONNECTED':
   color(cr,primary,.8+.15*sin(2*pi*3*p)); width(cr,r*.038); new_path(cr); arc(cr,cx,cy,r,0,2*pi); stroke(cr)
   color(cr,secondary,.6); width(cr,r*.012); new_path(cr); arc(cr,cx,cy,r*.89,0,2*pi); stroke(cr)
  color(cr,primary,.85 if state!='DISCONNECTED' else .48); width(cr,r*.046); line_cap(cr,1)
  if state=='CONNECTED':
   new_path(cr); move(cr,cx-r*.22,cy); line(cr,cx-r*.055,cy+r*.15); line(cr,cx+r*.25,cy-r*.17); stroke(cr)
  else:
   new_path(cr); arc(cr,cx,cy+r*.015,r*.24,-pi*.27,pi*1.27); stroke(cr)
   move(cr,cx,cy-r*.30); line(cr,cx,cy-r*.02); stroke(cr)
  return state

def output_video(path,width_px,height_px,fps,frames,draw):
 path.parent.mkdir(parents=True,exist_ok=True)
 ff=subprocess.Popen(['ffmpeg','-hide_banner','-loglevel','error','-y','-f','rawvideo','-pixel_format','bgra','-video_size',f'{width_px}x{height_px}','-framerate',str(fps),'-i','-','-an','-vf','scale=out_color_matrix=bt709:in_range=full:out_range=tv','-c:v','libx264','-preset','medium','-crf','20','-profile:v','high','-level:v','4.0','-pix_fmt','yuv420p','-color_primaries','bt709','-color_trc','bt709','-colorspace','bt709','-color_range','tv','-g',str(fps*2),'-keyint_min',str(fps*2),'-sc_threshold','0','-threads','2','-movflags','+faststart',str(path)],stdin=subprocess.PIPE)
 s=surface_create(0,width_px,height_px); cr=create(s)
 assert surface_stride(s)==width_px*4
 try:
  for frame in range(frames):
   draw(cr,frame/frames)
   surface_flush(s)
   ff.stdin.write(C.string_at(surface_data(s),height_px*width_px*4))
  ff.stdin.close(); assert ff.wait()==0
 finally:
  destroy(cr); surface_destroy(s)
  if ff.poll() is None: ff.kill(); ff.wait()

def main():
 parser=argparse.ArgumentParser(); parser.add_argument('--world',choices=(*WORLDS,'preview','all'),default='all')
 args=parser.parse_args(); renderer=Renderer()
 try:
  for world in (WORLDS if args.world=='all' else (args.world,) if args.world in WORLDS else ()):
   output_video(PACK/f'android/res/raw/tngl_motion_{world}.mp4',720,1600,24,288,lambda cr,p,w=world:renderer.scene(cr,w,p,720,1600))
   print(world,'rendered',flush=True)
  if args.world in ('preview','all'):
   def contact(cr,p):
    for i,world in enumerate(WORLDS):
     save(cr); translate(cr,i*270,0); rectangle(cr,0,0,270,600); clip(cr)
     renderer.scene(cr,world,p,270,600)
     state=renderer.portal(cr,world,p,135,275,64)
     color(cr,'#000000',.45); rectangle(cr,0,0,270,35); fill(cr)
     color(cr,'#F5F7F6',.9); font_face(cr,b'sans-serif',0,1); font_size(cr,11); move(cr,14,23); show_text(cr,world.upper().encode())
     color(cr,'#F5F7F6',.7); font_size(cr,9); move(cr,82,374); show_text(cr,state.encode())
     restore(cr)
   output_video(PACK/'reference/backgrounds-and-portals.mp4',1080,600,24,288,contact)
   print('preview rendered',flush=True)
 finally: renderer.release()

if __name__=='__main__': main()
