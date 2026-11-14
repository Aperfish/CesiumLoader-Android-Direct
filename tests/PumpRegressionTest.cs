using System;
using System.Collections.Generic;
using System.Threading;
using CesiumLoader.SDK.Runtime;
using CesiumLoader.SDK.Scheduling;

// Deterministic PlayerLoop substitute; MainThread and UpdateService are production source.
namespace Cysharp.Threading.Tasks {
 public enum PlayerLoopTiming {Update,PostLateUpdate}
 public static class PlayerLoopHelper {
  public static int MainThreadId=Thread.CurrentThread.ManagedThreadId;
  public static bool IsMainThread=>Thread.CurrentThread.ManagedThreadId==MainThreadId;
 }
 public static class UniTask {
  public static readonly Queue<Action> Queue=new Queue<Action>();
  public static void Post(Action a,PlayerLoopTiming timing){Queue.Enqueue(a);}
  public static void Frame(){int n=Queue.Count;while(n-->0)Queue.Dequeue()();}
 }
}
namespace CesiumLoader.SDK.Logging {
 public static class SdkLog {
  public static void Info(string tag,string msg){}
  public static void Warn(string tag,string msg){}
  public static void Error(string tag,string msg){}
  public static void Debug(string tag,string msg){}
  public static void ReportCrash(string tag,string msg,Exception e){throw new Exception(tag+": "+msg,e);}
 }
}
namespace CesiumLoader.SDK.Events {
 public static class UpdateEvents {public static void RaiseUpdate(){}public static void RaiseLateUpdate(){}}
}
namespace CesiumLoader.SDK.Mods {
 public sealed class ModContext {public static ModContext Current=>null;public void RegisterCleanup(Action a){}}
 public static class ModHost {public static void CheckConfigsOnMainThread(){}}
}
public static class PumpRegressionTest {
 public static void Main(string[] args){
  bool initialized=false,panelVisible=false;int lateTicks=0;
  // Android enters the loader from LoginScene.Awake, already on Unity's main thread.
  MainThread.ConfirmMainThread();
  if(args.Length==0)MainThread.Run(()=>initialized=true);else initialized=true;
  UpdateService.SubscribeUpdate(()=>panelVisible=true,null,"AndroidControls.OnUpdate");
  UpdateService.SubscribeLateUpdate(()=>lateTicks++,null,"AndroidSpeed.OnLateUpdate");
  for(int i=0;i<3;i++)Cysharp.Threading.Tasks.UniTask.Frame();
  Console.WriteLine("initialized="+initialized+" pump="+MainThread.IsPumpRunning+" panelUpdateReached="+panelVisible+" lateTicks="+lateTicks);
  if(!initialized||!panelVisible||lateTicks!=3)Environment.Exit(1);
  int queued=Cysharp.Threading.Tasks.UniTask.Queue.Count;
  MainThread.Initialize();MainThread.Initialize();
  if(Cysharp.Threading.Tasks.UniTask.Queue.Count!=queued)throw new Exception("duplicate pump registration");
  Console.WriteLine("PASS: main-thread loader drives panel updates and speed late updates exactly once per frame");
  MainThread.Shutdown();
 }
}
