using System;
using CesiumLoader.SDK.Mobile;
using SpeedHackMod;
public static class MobileLogicTest {
 static int count;static void Check(bool b,string label){count++;if(!b)throw new Exception(label);}
 public static void Main(){
  var g=new GestureState();float yaw,zoom;
  Check(!g.Step(1,1,2,0,0,100,0,100,false,out yaw,out zoom),"single touch stays with game");
  Check(g.Step(2,1,2,0,0,100,0,100,false,out yaw,out zoom)&&yaw==0&&zoom==0,"initial pair does not jump");
  g.Step(2,1,2,10,0,110,0,100,false,out yaw,out zoom);Check(yaw==0&&zoom==0,"horizontal translation never rotates or zooms");
  g.Step(2,2,1,110,10,10,10,100,false,out yaw,out zoom);Check(yaw==0&&Math.Abs(zoom-.8f)<.001,"finger enumeration swap and vertical zoom");
  g.Step(2,1,2,0,10,120,10,100,false,out yaw,out zoom);Check(yaw==0&&zoom==1,"pinch is bounded");
  g.Step(2,3,4,80,30,150,80,100,false,out yaw,out zoom);Check(yaw==0&&zoom==0,"new finger ids rebase");
  g.Step(2,3,4,90,30,160,80,100,true,out yaw,out zoom);Check(yaw==0&&zoom==0,"UI blocks gesture");
  g.Step(2,3,4,100,30,170,80,100,false,out yaw,out zoom);Check(yaw==0&&zoom==0,"cannot start halfway through a UI drag");
  Check(g.Step(1,3,4,0,0,0,0,100,false,out yaw,out zoom),"remaining finger does not become a click");
  Check(!g.Step(0,0,0,0,0,0,0,100,false,out yaw,out zoom),"release resets capture");
  g.Step(2,1,2,0,0,50,0,100,false,out yaw,out zoom);g.Step(1,1,2,0,0,50,0,100,false,out yaw,out zoom);
  g.Step(2,1,2,100,100,500,500,100,false,out yaw,out zoom);Check(yaw==0&&zoom==0,"one-to-two transition stays blocked until release");
  g.Reset();g.Step(2,1,2,0,0,100,0,100,false,out yaw,out zoom);g.Step(2,1,2,10000,0,10100,0,100,false,out yaw,out zoom);Check(yaw==0&&zoom==0,"large horizontal translation stays zoom-only");
  float value=1;var speed=new UnitySpeedScale(()=>value,v=>value=v);
  speed.Synchronize(4);Check(value==4,"apply scale");speed.Synchronize(4);Check(value==4,"no exponential multiplication");
  value=0;speed.Synchronize(4);Check(value==0,"never unpause game");speed.Restore();Check(value==0,"restore while paused is deferred");
  value=4;speed.Restore();Check(value==1,"deferred restore after resume");
  value=.5f;speed.Synchronize(4);Check(value==2,"respect slow motion baseline");value=.25f;speed.Restore();Check(value==.25f,"do not overwrite newer game value");
  value=1;speed.Synchronize(2);value=.5f;speed.Synchronize(2);Check(value==1,"adopt later game baseline");speed.Restore();Check(value==.5f,"restore updated baseline");
  float step=.02f;var fixedStep=new FixedStepBudget(()=>step,()=>.333f,v=>step=v);
  fixedStep.Synchronize(4);Check(Math.Abs(step-.08f)<.00001,"fixed step scale");fixedStep.Synchronize(4);Check(Math.Abs(step-.08f)<.00001,"fixed step stable");
  fixedStep.Restore();Check(Math.Abs(step-.02f)<.00001,"fixed step restore");
  try{speed.Synchronize(double.NaN);Check(false,"NaN rejected");}catch(ArgumentOutOfRangeException){Check(true,"NaN rejected");}
  Console.WriteLine("PASS "+count+" mobile logic assertions");
 }
}
