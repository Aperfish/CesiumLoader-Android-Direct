using System;
using System.IO;
using CesiumAndroid;
public static class ApiRegistryTest {
 static int checks;
 static void Check(bool b,string text){checks++;if(!b)throw new Exception(text);}
 public static void Main(string[] args){
  UnityEngine.Application.persistentDataPath=Path.GetFullPath(args[0]);
  Check(AndroidApi.Root==Path.Combine(UnityEngine.Application.persistentDataPath,"CesiumLoader"),"game-owned root");
  Check(AndroidApi.GetModConfigPath("Test")==Path.Combine(AndroidApi.Root,"mods","Test","config.json"),"existing SDK config path");
  bool on=false;int clicks=0;
  AndroidApi.RegisterToggle("SpeedHackMod","active","加速",()=>on,v=>on=v);
  AndroidApi.RegisterButton("SpeedHackMod","preset","2x",()=>clicks++);
  var items=AndroidApi.GetActions("SpeedHackMod");Check(items.Count==2,"registered actions visible to overlay");
  items[0].Set(true);Check(items[0].Get(),"toggle invokes setter and getter");items[1].Run();Check(clicks==1,"button invokes action");
  AndroidApi.RegisterButton("SpeedHackMod","preset","4x",()=>clicks+=4);
  Check(AndroidApi.GetActions("SpeedHackMod").Count==2,"re-register does not duplicate rows");
  AndroidApi.GetActions("SpeedHackMod")[1].Run();Check(clicks==5,"updated callback used");
  AndroidApi.RegisterMod("SpeedHackMod","变速控制");Check(AndroidApi.GetModName("SpeedHackMod")=="变速控制","display name");
  AndroidApi.RegisterButton("implicit","implicit caller",()=>{});
  Check(AndroidApi.GetActions("ApiRegistryTest").Count==1,"implicit registration belongs to caller assembly");
  AndroidApi.WriteText("configs/example.json","test");Check(AndroidApi.ReadText("configs/example.json")=="test","relative file IO");
  Check(AndroidApi.EnsureConfig("example.json","other")=="test","preserve existing config");
  Directory.CreateDirectory(AndroidApi.ModsDirectory);
  for(int i=0;i<30;i++)File.WriteAllBytes(Path.Combine(AndroidApi.ModsDirectory,"Mod"+i+".dll"),new byte[0]);
  var ids=AndroidApi.GetModIds();Check(ids.Contains("Mod29")&&ids.Contains("SpeedHackMod"),"flat mod files plus registered actions");
  Check(AndroidApi.GetActions("speedhackmod").Count==2,"case-insensitive action grouping");
  Console.WriteLine("PASS "+checks+" AndroidApi registry/path/action assertions");
 }
}
