using System;
using FairyGUI;
using UnityEngine;
using CesiumLoader.SDK.Audio;
namespace CesiumLoader.SDK.Mobile {
 public static class MobileNotice {
  static GTextField label;static float until;
  public static bool IsAvailable {get{return GRoot.inst!=null;}}
  public static bool SupportsSound {get{return AudioService.IsAvailable;}}
  public static string UnavailableReason {get{return "安卓仅提供游戏内提醒，不支持后台应用置前";}}
  public static void Show(string what){
   try{
    if(GRoot.inst==null)return;
    if(label==null||label.isDisposed||label.parent==null){
     label=new GTextField{touchable=false,sortingOrder=32720};var f=label.textFormat;f.size=28;f.color=new Color(1,.88f,.4f,1);f.align=AlignType.Center;label.textFormat=f;GRoot.inst.AddChild(label);
    }
    label.text=what;label.SetSize(Math.Min(700,GRoot.inst.width-24),56);label.SetXY((GRoot.inst.width-label.width)/2,58);label.visible=true;until=Time.realtimeSinceStartup+4;
   }catch(Exception e){Debug.LogWarning("[CesiumAndroid] notice: "+e.Message);}
  }
  public static void Tick(){if(label!=null&&!label.isDisposed&&Time.realtimeSinceStartup>=until)label.visible=false;}
 }
}
