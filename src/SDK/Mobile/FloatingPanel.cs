using System;
using FairyGUI;
using UnityEngine;
namespace CesiumLoader.SDK.Mobile {
 // FairyGUI receives touches through the game's own event routing. No Android overlay permission.
 public sealed class FloatingPanel : IDisposable {
  public GComponent Root {get;private set;}
  GComponent body,header; GTextField title; GGraph background;
  float width,height,initialX,initialY,lastW,lastH; bool collapsed;
  Action<bool> changed;
  public bool IsAlive {get{return Root!=null&&!Root.isDisposed&&Root.parent!=null;}}
  public FloatingPanel(string name,string caption,float w,float h,float x,float y,bool folded,Action<bool> onFold=null){
   width=w;height=h;initialX=x;initialY=y;collapsed=folded;changed=onFold;
   Root=new GComponent{name="CesiumAndroid_"+name,sortingOrder=32700,opaque=true};
   background=new GGraph{touchable=false};Root.AddChild(background);
   body=new GComponent();body.SetXY(0,52);Root.AddChild(body);
   header=new GComponent{opaque=true,draggable=true};header.SetSize(w-56,52);Root.AddChild(header);
   title=Label(header,caption,20,10,12,w-72,32);
   header.onTouchBegin.Add(MobileTouch.BlockGestures);
   header.onDragStart.Add((EventContext ctx)=>{ctx.PreventDefault();Root.StartDrag(ctx.inputEvent.touchId);});
   Root.onDragEnd.Add(()=>Clamp());
   var fold=Button(Root,"≡",w-54,2,52,48,()=>{collapsed=!collapsed;Resize();changed?.Invoke(collapsed);});
   fold.name="Collapse";
   GRoot.inst.AddChild(Root);Root.SetXY(x,y);Resize();Clamp();
  }
  public void SetTitle(string s){if(title!=null)title.text=s;}
  public GComponent AddButton(string text,float x,float y,float w,float h,Action click){return Button(body,text,x,y,w,h,click);}
  public GTextField AddLabel(string text,float x,float y,float w,float h,int size=18){return Label(body,text,size,x,y,w,h);}
  public void Tick(){
   if(!IsAlive)return;
   float w=GRoot.inst.width,h=GRoot.inst.height;
   if(lastW!=w||lastH!=h){lastW=w;lastH=h;Clamp();}
  }
  public void Clamp(){
   if(Root==null||GRoot.inst==null)return;
   float rw=Math.Max(1,GRoot.inst.width),rh=Math.Max(1,GRoot.inst.height);
   Rect safe=Screen.safeArea;
   float left=8+safe.x/Math.Max(1,Screen.width)*rw,right=(safe.x+safe.width)/Math.Max(1,Screen.width)*rw-8;
   float top=8+(Screen.height-safe.y-safe.height)/Math.Max(1,Screen.height)*rh,bottom=(Screen.height-safe.y)/Math.Max(1,Screen.height)*rh-8;
   if(right<=left||bottom<=top){left=top=8;right=rw-8;bottom=rh-8;}
   float density=Screen.dpi>0?Math.Max(1,Screen.dpi/160):Math.Max(1,Screen.height/480f);
   float desired=Math.Max(1,Math.Min(2.5f,48*density/(42*Math.Max(.1f,Screen.width/rw))));
   float s=Math.Min(desired,Math.Min((right-left)/width,(bottom-top)/height));
   Root.SetScale(s,s);
   Root.SetXY(Math.Max(left,Math.Min(Root.x,right-width*s)),Math.Max(top,Math.Min(Root.y,bottom-Root.height*s)));
  }
  void Resize(){body.visible=!collapsed;Root.SetSize(width,collapsed?52:height);background.DrawRect(width,Root.height,1,new Color(.3f,.4f,.55f,1),new Color(.035f,.05f,.08f,.94f));Clamp();}
  static GTextField Label(GComponent p,string s,int size,float x,float y,float w,float h){
   var t=new GTextField{touchable=false,text=s};var f=t.textFormat;f.size=size;f.color=Color.white;t.textFormat=f;
   t.SetXY(x,y);t.SetSize(w,h);p.AddChild(t);return t;
  }
  static GComponent Button(GComponent p,string s,float x,float y,float w,float h,Action click){
   var b=new GComponent{opaque=true};b.SetXY(x,y);b.SetSize(w,h);p.AddChild(b);
   var g=new GGraph{touchable=false};g.DrawRect(w,h,1,new Color(.3f,.45f,.6f,1),new Color(.12f,.19f,.27f,1));b.AddChild(g);
   var label=Label(b,s,19,6,10,w-12,h-12);var tf=label.textFormat;tf.align=AlignType.Center;label.textFormat=tf;
   b.onTouchBegin.Add(MobileTouch.BlockGestures);
   b.onClick.Add(()=>{try{click();}catch(Exception e){Debug.LogError("[CesiumAndroid] button: "+e);}});
   return b;
  }
  public void Dispose(){if(Root!=null&&!Root.isDisposed)Root.Dispose();Root=null;}
 }
}
