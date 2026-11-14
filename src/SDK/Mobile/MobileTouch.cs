using System;
using System.Reflection;
using FairyGUI;
using UnityEngine;
using CesiumLoader.SDK.Runtime;
using CesiumLoader.SDK.Inputs;
namespace CesiumLoader.SDK.Mobile {
 public static class MobileTouch {
  static readonly GestureState gesture=new GestureState();
  static object camera; static Type cameraType;
  static MethodInfo getTouch;static PropertyInfo touchCount;static Type touchType;
  static PropertyInfo position,phase,fingerId;static float nextProbe;
  static int touchFrame=-1,count;static Vector2 a,b;static int aid,bid;static bool ended,blockedByUi;
  static bool focusLost;static int lastCameraFrame=-1;static bool lastConsumed;
  static bool overlayOpen;
  public static void SetOverlayOpen(bool value){overlayOpen=value;if(value)gesture.Block();}
  public static void ZoomCamera(float delta){if(camera!=null)Call("HandleZoomScroll",delta);}
  public static bool HasCamera {get{return camera!=null;}}
  public static bool GestureActive {get{return gesture.Consuming;}}
  public static void BlockGestures(){gesture.Block();}
  public static void AttachCamera(object value){camera=value;cameraType=value?.GetType();gesture.Reset();}
  public static void RestoreCamera(){if(camera==null)return;Set("_restoreRequested",true);Set("_restoreReason","触屏复位");gesture.Block();}
  static object Get(string name){return cameraType.GetField(name,BindingFlags.Instance|BindingFlags.NonPublic)?.GetValue(camera);}
  static void Set(string name,object v){cameraType.GetField(name,BindingFlags.Instance|BindingFlags.NonPublic)?.SetValue(camera,v);}
  static object Call(string name,params object[] p){return cameraType.GetMethod(name,BindingFlags.Instance|BindingFlags.NonPublic)?.Invoke(camera,p);}
  static bool UiTarget(){
   try{var g=GRoot.inst?.touchTarget;while(g!=null){if(g.name!=null&&g.name.StartsWith("CesiumAndroid_"))return true;if(g is GButton||g is GTextInput||g is GList)return true;g=g.parent;}}catch{}
   return false;
  }
  static void Read(){
   if(touchFrame==Time.frameCount)return;touchFrame=Time.frameCount;count=0;ended=false;blockedByUi=false;
   if(!Application.isFocused){focusLost=true;gesture.Reset();return;}
   if(touchCount==null){
    if(Time.realtimeSinceStartup<nextProbe)return;nextProbe=Time.realtimeSinceStartup+2;
    var input=RuntimeAssemblyService.FindType("UnityEngine.Input");
    if(input==null)return;
    touchCount=input.GetProperty("touchCount",BindingFlags.Public|BindingFlags.Static);
    getTouch=input.GetMethod("GetTouch",new[]{typeof(int)});
    touchType=getTouch?.ReturnType;position=touchType?.GetProperty("position");phase=touchType?.GetProperty("phase");fingerId=touchType?.GetProperty("fingerId");
   }
   try{
    count=touchCount==null?0:Convert.ToInt32(touchCount.GetValue(null,null));
    if(count==2&&getTouch!=null){
     var t0=getTouch.Invoke(null,new object[]{0});var t1=getTouch.Invoke(null,new object[]{1});
     a=(Vector2)position.GetValue(t0,null);b=(Vector2)position.GetValue(t1,null);
     aid=Convert.ToInt32(fingerId.GetValue(t0,null));bid=Convert.ToInt32(fingerId.GetValue(t1,null));
     var p0=phase.GetValue(t0,null).ToString();var p1=phase.GetValue(t1,null).ToString();
     ended=p0=="Ended"||p0=="Canceled"||p1=="Ended"||p1=="Canceled";
     blockedByUi=UiTarget()||OverUi(aid)||OverUi(bid);
    }
    if(focusLost){if(count>0)gesture.Block();else focusLost=false;}
   }catch{count=0;gesture.Reset();}
  }
  static bool OverUi(int id){
   try{
    // Query FairyGUI's own target for each finger, including the second one.
    var stage=Stage.inst;var method=stage.GetType().GetMethod("GetTouchTarget",new[]{typeof(int)});
    if(method==null)return false;
    object display=method.Invoke(stage,new object[]{id});
    for(int i=0;display!=null&&i<30;i++){
     var type=display.GetType();var field=type.GetField("gOwner");var prop=type.GetProperty("gOwner");
     var owner=(field!=null?field.GetValue(display):prop?.GetValue(display,null)) as GObject;
     while(owner!=null){if(owner is GButton||owner is GTextInput||owner is GList||(owner.name??"").StartsWith("CesiumAndroid_"))return true;owner=owner.parent;}
     display=type.GetProperty("parent")?.GetValue(display,null);
    }
   }catch{}
   return false;
  }
  // Called before the original OnUpdate. Only consume touch gestures; hardware keyboard remains usable.
  public static bool UpdateCamera(object value){
   if(!ReferenceEquals(value,camera))AttachCamera(value);
   if(lastCameraFrame==Time.frameCount)return lastConsumed;lastCameraFrame=Time.frameCount;
   Read();float yaw,zoom;
   if(overlayOpen){gesture.Block();lastConsumed=true;return true;}
   bool consume=gesture.Step(count,aid,bid,a.x,a.y,b.x,b.y,Math.Max(1,Math.Min(Screen.width,Screen.height)),blockedByUi||ended,out yaw,out zoom);
   lastConsumed=consume;
   if(!consume)return false;
   try{
    Set("_rotateHeldNow",count==2&&!blockedByUi&&!ended);
    Call("MaintainAutoFollow");
    if(ended||blockedByUi)return true;
    if(Math.Abs(zoom)>.00001f)Call("HandleZoomScroll",zoom*((bool)(Get("_invertWheel")??false)?-1:1));
    if(Math.Abs(yaw)>.00001f&&(bool)(Call("TryBeginDrive","双指滑动")??false)){
     if((bool)(Get("_invertRotate")??false))yaw=-yaw;
     float target=(float)Get("_targetYawDelta")+yaw;Set("_targetYawDelta",target);
     if(!(bool)Get("_smooth"))Set("_yawDelta",target);
    }
    Set("_lastFollowInteractionAt",Time.realtimeSinceStartup);
   }catch(Exception e){Debug.LogError("[CesiumAndroid] camera gesture: "+e);}
   return true;
  }
 }
}
