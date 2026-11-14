// Adaptation of TenkinMori/CesiumLoader-Android-MOD's overlay: same AndroidApi action registry,
// icon, mod list and per-mod action view. FairyGUI hosts the view instead of a hot-update MonoBehaviour.
// Upstream MIT notice is included in the installed licenses directory.
using System;
using System.Collections.Generic;
using System.IO;
using CesiumAndroid;
using CesiumLoader.SDK.Mods;
using CesiumLoader.SDK.Mobile;
using CesiumLoader.SDK.Manifests;
using FairyGUI;
using UnityEngine;
[assembly:ModManifest("Cesium 安卓悬浮窗", "0.4.0", "TenkinMori / Android adaptation", "AndroidApi 模组列表与功能面板",Permissions=ModPermission.ReadGameState|ModPermission.UI)]
namespace CesiumOverlayMod {
 public static class ModEntry {public static void Main(){ModBase.Run(new OverlayController(),tag:"CesiumOverlay");}}
 public sealed class OverlayController:ModBase {
  GComponent root,window,icon;GTextField title;NTexture iconTexture;
  readonly List<GObject> rows=new List<GObject>();
  bool open;string selected="SpeedHackMod";int modPage,actionPage;float width,height,nextBuild,nextRefresh;string contentKey;
  public override string Name=>"Cesium 安卓悬浮窗";
  public override string Version=>"0.4.0";
  public override void OnInitialize(){AndroidApi.RegisterMod("CesiumOverlayMod","Cesium 安卓悬浮窗");Log.Info("使用 TenkinMori AndroidApi；头像按钮打开左侧模组、右侧功能面板。");}
  public override void OnUpdate(){
   if(GRoot.inst==null)return;
   if(root==null||root.isDisposed||root.parent!=GRoot.inst||width!=GRoot.inst.width||height!=GRoot.inst.height){
    if(Time.realtimeSinceStartup<nextBuild)return;nextBuild=Time.realtimeSinceStartup+2;Build();
   }
   MobileTouch.SetOverlayOpen(open);
   if(open&&Time.realtimeSinceStartup>=nextRefresh){nextRefresh=Time.realtimeSinceStartup+.3f;Render();}
  }
  void Build(){
   if(root!=null&&!root.isDisposed)root.Dispose();rows.Clear();contentKey=null;
   width=GRoot.inst.width;height=GRoot.inst.height;
   root=new GComponent{name="CesiumAndroid_RepositoryOverlay",sortingOrder=32740};root.SetSize(width,height);GRoot.inst.AddChild(root);
   icon=new GComponent{name="CesiumAndroid_Avatar",opaque=true,draggable=true};icon.SetSize(112,112);icon.SetXY(28,90);root.AddChild(icon);
   Box(icon,112,112,new Color(.09f,.13f,.22f,.98f));
   try{
    if(iconTexture==null){
     int w,h;byte[] rgba;
     if(PngDecoder.TryDecode(File.ReadAllBytes(AndroidApi.GetLoaderPath("overlay.png")),out w,out h,out rgba)){
      for(int y=0;y<h/2;y++)for(int x=0;x<w*4;x++){int a=y*w*4+x,b=(h-1-y)*w*4+x;byte t=rgba[a];rgba[a]=rgba[b];rgba[b]=t;}
      var tex=new Texture2D(w,h,TextureFormat.RGBA32,false);tex.LoadRawTextureData(rgba);tex.Apply(false,false);iconTexture=new NTexture(tex);
     }
    }
    if(iconTexture!=null){var image=new GLoader{touchable=false,texture=iconTexture,fill=FillType.ScaleFree};image.SetSize(112,112);icon.AddChild(image);}
    else Text(icon,"Mods",22,8,36,96,40);
   }catch(Exception e){Text(icon,"Mods",22,8,36,96,40);Log.Warn("头像读取失败，使用文字入口: "+e.Message);}
   icon.onTouchBegin.Add(MobileTouch.BlockGestures);icon.onClick.Add(()=>{open=!open;window.visible=open;MobileTouch.SetOverlayOpen(open);contentKey=null;Render();});
   icon.onDragEnd.Add(()=>icon.SetXY(Mathf.Clamp(icon.x,12,Math.Max(12,width-124)),Mathf.Clamp(icon.y,12,Math.Max(12,height-124))));
   window=new GComponent{name="CesiumAndroid_Window",opaque=true};window.SetSize(1000,700);root.AddChild(window);Box(window,1000,700,new Color(.035f,.05f,.08f,.97f));
   float scale=Math.Min(1.4f,Math.Min((width-32)/1000,(height-32)/700));window.SetScale(scale,scale);window.SetXY((width-1000*scale)/2,(height-700*scale)/2);window.visible=open;
   var header=new GComponent{opaque=true,draggable=true};header.SetSize(916,64);window.AddChild(header);title=Text(header,"Cesium Mods",26,20,16,880,44);
   header.onTouchBegin.Add(MobileTouch.BlockGestures);header.onDragStart.Add((EventContext c)=>{c.PreventDefault();window.StartDrag(c.inputEvent.touchId);});
   window.onDragEnd.Add(()=>window.SetXY(Mathf.Clamp(window.x,8,Math.Max(8,width-window.width*scale-8)),Mathf.Clamp(window.y,8,Math.Max(8,height-window.height*scale-8))));
   Button(window,"关闭",922,8,68,50,()=>{open=false;window.visible=false;MobileTouch.SetOverlayOpen(false);});
   window.onTouchBegin.Add(MobileTouch.BlockGestures);
   Render();Log.Info("头像悬浮入口已创建，API 根目录="+AndroidApi.Root);
  }
  void Render(){
   if(!open||window==null)return;
   var mods=AndroidApi.GetModIds();
   if(mods.Count==0)return;
   if(!mods.Contains(selected)){selected=mods[0];actionPage=0;}
   int modPages=Math.Max(1,(mods.Count+6)/7);modPage=Math.Max(0,Math.Min(modPage,modPages-1));
   var actions=AndroidApi.GetActions(selected);int actionPages=Math.Max(1,(actions.Count+5)/6);actionPage=Math.Max(0,Math.Min(actionPage,actionPages-1));
   string key=selected+"|"+modPage+"|"+actionPage+"|"+mods.Count+"|"+actions.Count;
   foreach(var action in actions){try{key+="|"+action.Label+(action.IsToggle?":"+action.Get():"");}catch{key+="|unavailable";}}
   if(key==contentKey)return;contentKey=key;
   foreach(var row in rows)if(!row.isDisposed)row.Dispose();rows.Clear();
   title.text="Cesium Mods · "+AndroidApi.GetModName(selected);
   for(int i=0;i<7;i++){
    int index=modPage*7+i;if(index>=mods.Count)break;string id=mods[index];
    rows.Add(Button(window,(id==selected?"● ":"")+AndroidApi.GetModName(id),14,82+i*70,314,62,()=>{selected=id;actionPage=0;contentKey=null;Render();}));
   }
   for(int i=0;i<6;i++){
    int index=actionPage*6+i;if(index>=actions.Count)break;ActionItem item=actions[index];bool value=false;string text=item.Label;
    try{if(item.IsToggle){value=item.Get();text+=(value?"  [ON]":"  [OFF]");}}catch{text+="（暂不可用）";}
    rows.Add(Button(window,text,350,82+i*78,634,68,()=>{
     try{if(item.IsToggle)item.Set(!item.Get());else item.Run?.Invoke();}catch(Exception e){AndroidApi.LogError(item.ModId+"/"+item.Id+": "+e);MobileNotice.Show("操作失败，详见模组日志");}
     contentKey=null;
    }));
   }
   if(actions.Count==0)rows.Add(Text(window,"此模组自动运行，当前没有手动操作。",24,360,106,600,80));
   rows.Add(Button(window,"上一页",14,594,118,58,()=>{modPage=(modPage+modPages-1)%modPages;contentKey=null;}));
   rows.Add(Text(window,(modPage+1)+" / "+modPages,22,140,610,76,38));
   rows.Add(Button(window,"下一页",218,594,110,58,()=>{modPage=(modPage+1)%modPages;contentKey=null;}));
   rows.Add(Button(window,"上一页",350,594,160,58,()=>{actionPage=(actionPage+actionPages-1)%actionPages;contentKey=null;}));
   rows.Add(Text(window,(actionPage+1)+" / "+actionPages,22,604,610,110,38));
   rows.Add(Button(window,"下一页",824,594,160,58,()=>{actionPage=(actionPage+1)%actionPages;contentKey=null;}));
  }
  static void Box(GComponent p,float w,float h,Color color){var g=new GGraph{touchable=false};g.DrawRect(w,h,1,new Color(.3f,.4f,.55f,1),color);p.AddChild(g);}
  static GTextField Text(GComponent p,string s,int size,float x,float y,float w,float h){var t=new GTextField{touchable=false,text=s};var f=t.textFormat;f.size=size;f.color=Color.white;t.textFormat=f;t.SetSize(w,h);t.SetXY(x,y);p.AddChild(t);return t;}
  static GComponent Button(GComponent p,string s,float x,float y,float w,float h,Action action){var b=new GComponent{opaque=true};b.SetXY(x,y);b.SetSize(w,h);p.AddChild(b);Box(b,w,h,new Color(.13f,.2f,.3f,1));Text(b,s,23,12,14,w-24,h-18);b.onTouchBegin.Add(MobileTouch.BlockGestures);b.onClick.Add(()=>action());return b;}
  public override void OnUnload(){MobileTouch.SetOverlayOpen(false);if(root!=null&&!root.isDisposed)root.Dispose();iconTexture?.Dispose();iconTexture=null;}
 }
}
