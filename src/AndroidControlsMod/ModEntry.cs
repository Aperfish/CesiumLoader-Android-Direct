using System;
using CesiumLoader.SDK.Mods;
using CesiumLoader.SDK.Mobile;
using CesiumLoader.SDK.Manifests;
using UnityEngine;
using FairyGUI;
using CesiumAndroid;
[assembly:ModManifest("安卓触屏控制", "0.4.0", "Cesium Android", "触屏入口与镜头复位", Permissions=ModPermission.ReadGameState|ModPermission.UI)]
namespace AndroidControlsMod {
 public static class ModEntry {public static void Main(){ModBase.Run(new Controls(),tag:"AndroidControls");}}
 public sealed class Controls:ModBase {
  bool hadFocus=true;int registryCount=-1;
  public override string Name=>"安卓触屏控制";
  public override void OnUpdate(){
   MobileNotice.Tick();
   if(!Application.isFocused){if(hadFocus)VirtualKeys.Clear();hadFocus=false;return;}hadFocus=true;
   if(registryCount!=ModRegistry.Count)Build();
  }
  void Action(string id,string key,string fallback,bool held=false){
   var c=ModRegistry.Get(id);if(c==null)return;var s=c.Config;KeyCode k;
   if(!Enum.TryParse(s?.GetString(key,fallback)??fallback,true,out k)||k==KeyCode.None)return;
   bool latch=held&&(id=="HideHudMod"?(s?.GetString("pressMode","Toggle")??"Toggle").Equals("Hold",StringComparison.OrdinalIgnoreCase):(s?.GetBool("holdMode",false)??false));
   if(latch)VirtualKeys.ToggleHeld(k);else VirtualKeys.Pulse(k);
  }
  void Build(){
   registryCount=ModRegistry.Count;
   foreach(var pair in ModNames.All)AndroidApi.RegisterMod(pair.Key,pair.Value);
   foreach(var ctx in ModRegistry.All){
    string id=ctx.ModId;
    AndroidApi.RegisterButton(id,"android.status","查看运行状态",()=>MobileNotice.Show(AndroidApi.GetModName(id)+"："+ctx.State));
    string key=ctx.Config.Has("enabled")?"enabled":ctx.Config.Has("Enabled")?"Enabled":null;
    var instance=ctx.Mod as ModBase;var changed=instance?.GetType().GetMethod("OnConfigChanged");
    if(key!=null&&instance!=null&&changed!=null&&changed.DeclaringType!=typeof(ModBase)){
     AndroidApi.RegisterToggle(id,"android.enabled","模组总开关",()=>ctx.Config.GetBool(key,true),value=>{ctx.Config.Set(key,value);ctx.Config.SaveIfDirty();instance.OnConfigChanged();});
    }
   }
   Add("FreeCameraMod","camera.reset","恢复镜头",()=>MobileTouch.RestoreCamera());
   Add("FreeCameraMod","camera.near","拉近镜头",()=>MobileTouch.ZoomCamera(1));
   Add("FreeCameraMod","camera.far","拉远镜头",()=>MobileTouch.ZoomCamera(-1));
   Add("HideHudMod","hud.toggle","隐藏 / 恢复 HUD",()=>Action("HideHudMod","toggleKey","BackQuote",true));
   Add("PveWaveForecastMod","wave.toggle","刷怪预报开关",()=>Action("PveWaveForecastMod","toggleKey","O"));
   Add("ShopRelicPeekMod","shop.clear","清空商店看板",()=>Action("ShopRelicPeekMod","clearKey","T"));
   Add("StarCoinShopMod","shop.buy","星币商店快捷购买",()=>Action("StarCoinShopMod","quickBuyKey","F"));
   if(ModRegistry.Get("PvePanelMod")!=null){
    Add("PvePanelMod","pve.left","PVE 左栏",()=>Action("PvePanelMod","LeftToggleKey","F3"));
    Add("PvePanelMod","pve.hand","PVE 手牌",()=>Action("PvePanelMod","HandToggleKey","F4"));
    Add("PvePanelMod","pve.right","PVE 右栏",()=>Action("PvePanelMod","RightToggleKey","F5"));
   }
   Log.Info("触屏功能已注册到 TenkinMori AndroidApi。");
  }
  void Add(string id,string key,string text,Action action){if(ModRegistry.Get(id)!=null)AndroidApi.RegisterButton(id,key,text,action);}
  public override void OnUnload(){VirtualKeys.Clear();}
 }
}
