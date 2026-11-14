using System;
using System.Globalization;
using CesiumLoader.SDK.Mods;
using CesiumLoader.SDK.Mobile;
using CesiumLoader.SDK.Manifests;
using UnityEngine;
using FairyGUI;
using CesiumAndroid;
[assembly:ModManifest("安卓变速面板", "0.4.0", "Cesium Android", "Unity 倍率与触屏悬浮面板",Permissions=ModPermission.ReadGameState|ModPermission.UI|ModPermission.SpeedHack)]
namespace SpeedHackMod {
 public static class ModEntry {public static void Main(){ModBase.Run(new AndroidSpeed(),tag:"AndroidSpeed");}}
 public sealed class AndroidSpeed:ModBase {
  readonly UnitySpeedScale scale=new UnitySpeedScale(UnityScaleAccess.Read,UnityScaleAccess.Write);
  readonly FixedStepBudget fixedStep=new FixedStepBudget(UnityFixedStepAccess.ReadStep,UnityFixedStepAccess.ReadMaximum,UnityFixedStepAccess.WriteStep);
  readonly FairyUiTimeScaleBridge ui=new FairyUiTimeScaleBridge();
  readonly IndependentAnimationBridge independent=new IndependentAnimationBridge(UnityAnimationDiscovery.FindType,UnityAnimationDiscovery.Find);
  double speed=1,memory=2,step=.5,max=10;float saveAt;bool dirty,uiFailed,animationFailed,fixedFailed,enabled=true;
  public override string Name=>"安卓变速面板";
  public override void OnInitialize(){
   memory=Math.Max(1,Math.Min(10,Config?.GetDouble("speed",2)??2));step=Math.Max(.1,Math.Min(2,Config?.GetDouble("speedStep",.5)??.5));max=Math.Max(1,Math.Min(10,Config?.GetDouble("maxSpeed",10)??10));
   independent.Report=s=>Log.Info(s);independent.RegisterUiRoot=o=>ui.RegisterExternalRoot(o);
   AndroidApi.RegisterMod("SpeedHackMod","变速控制（Unity）");
   AndroidApi.RegisterToggle("SpeedHackMod","speed.active","开启记忆倍率",()=>speed>1.0001,v=>Choose(v?memory:1));
   AndroidApi.RegisterButton("SpeedHackMod","speed.down","降低 "+step.ToString("0.#")+"x",()=>Choose(speed-step));
   AndroidApi.RegisterButton("SpeedHackMod","speed.up","提高 "+step.ToString("0.#")+"x",()=>Choose(speed+step));
   AndroidApi.RegisterButton("SpeedHackMod","speed.reset","恢复 1x",()=>Choose(1));
   double[] presets={1,1.5,2,3,4,6,8,10};foreach(double preset in presets){double p=preset;AndroidApi.RegisterButton("SpeedHackMod","speed."+p.ToString(CultureInfo.InvariantCulture),"设置 "+p.ToString("0.#")+"x",()=>Choose(p));}
   Log.Info("安卓 Unity 变速已注册到 TenkinMori AndroidApi；系统时钟不变；启动保持 1x。");
  }
  void Choose(double value){
   speed=Math.Max(1,Math.Min(max,value));if(speed>1.0001)memory=speed;
   dirty=true;saveAt=Time.realtimeSinceStartup+.6f;
   try{Apply();}catch(Exception e){Fault(e);}Refresh();
  }
  void Refresh(){foreach(var item in AndroidApi.GetActions("SpeedHackMod"))if(item.Id=="speed.active")item.Label=enabled?"Unity 目标 "+speed.ToString("0.#")+"x（系统时钟 1x）":"变速接口不可用，已停止";}
  void Apply(){
   if(!enabled)return;
   if(!Application.isFocused){Restore();return;}
   scale.Synchronize(speed);
   if(!fixedFailed)try{if(Config?.GetBool("optimizeFixedUpdates",true)??true)fixedStep.Synchronize(speed);else fixedStep.Restore();}catch(Exception e){fixedFailed=true;Log.Warn("固定步长不可用: "+e.Message);}
   if(speed<=1.0001||UnityScaleAccess.Read()==0){ui.Restore();independent.Restore();return;}
   if(!animationFailed)try{if(Config?.GetBool("accelerateIndependentAnimations",true)??true)independent.Tick(Time.realtimeSinceStartup,speed);else independent.Restore();}catch(Exception e){animationFailed=true;independent.Restore();Log.Warn("独立动画同步停用: "+e.Message);}
   if(!uiFailed)try{if(Config?.GetBool("accelerateUiAnimations",true)??true)ui.Tick(Time.realtimeSinceStartup);else ui.Restore();}catch(Exception e){uiFailed=true;ui.Restore();Log.Warn("UI 动画同步停用: "+e.Message);}
  }
  void Fault(Exception e){enabled=false;speed=1;try{Restore();}catch{}Log.ReportCrash("AndroidSpeed",e);Refresh();}
  void Restore(){try{ui.Restore();}finally{try{independent.Restore();}finally{try{fixedStep.Restore();}finally{scale.Restore();}}}}
  public override void OnUpdate(){
   if(dirty&&Time.realtimeSinceStartup>=saveAt){dirty=false;Config?.Set("speed",memory);Config?.Set("accelerationMode","unity");Config?.SaveIfDirty();}
  }
  public override void OnLateUpdate(){try{Apply();}catch(Exception e){Fault(e);}}
  public override void OnUnload(){Restore();}
 }
}
