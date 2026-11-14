using System;
using System.IO;
using System.Linq;
using System.Collections.Generic;
using Mono.Cecil;
using Mono.Cecil.Cil;
public static class PortMods {
 public static void Main(string[] args){
  var resolver=new DefaultAssemblyResolver();resolver.AddSearchDirectory(Path.GetDirectoryName(args[2]));resolver.AddSearchDirectory(args[3]);
  using var sdk=ModuleDefinition.ReadModule(args[2],new ReaderParameters{AssemblyResolver=resolver});
  using var mod=ModuleDefinition.ReadModule(args[0],new ReaderParameters{AssemblyResolver=resolver});
  MethodReference Find(string type,string name,int count=-1){var m=sdk.GetType(type).Methods.Single(x=>x.Name==name&&(count<0||x.Parameters.Count==count));return mod.ImportReference(m);}
  int env=0,paths=0;
  foreach(var t in All(mod.Types))foreach(var method in t.Methods){
   if(!method.HasBody)continue;
   foreach(var ins in method.Body.Instructions.ToArray()){
    if(ins.Operand is MethodReference m&&m.DeclaringType.FullName=="System.Environment"&&m.Name=="GetEnvironmentVariable"&&m.Parameters.Count==1){ins.Operand=Find("CesiumLoader.SDK.AndroidPlatform","GetEnvironmentVariable",1);env++;}
    if(ins.Operand is MethodReference w&&w.DeclaringType.FullName=="CesiumLoader.SDK.Windowing.WindowFocus"&&(w.Name=="get_IsAvailable"||w.Name=="get_SupportsSound"||w.Name=="get_UnavailableReason"))ins.Operand=Find("CesiumLoader.SDK.Mobile.MobileNotice",w.Name,0);
    if(ins.Operand is MethodReference c&&c.DeclaringType.FullName=="UI.UINewGameLibrary_Button_Card"&&c.Name=="get_CurrentCardId"){
     method.Body.GetILProcessor().InsertBefore(ins,Instruction.Create(OpCodes.Ldarg_1));ins.OpCode=OpCodes.Call;ins.Operand=Find("CesiumLoader.SDK.Mobile.MobileCompatibility","GalleryCardId",2);
    }
    if(ins.OpCode==OpCodes.Ldstr&&ins.Operand is string s&&s.Contains("\\")&&!s.Contains(":\\")&&(s.StartsWith("mods\\")||s.StartsWith("data\\")||s.StartsWith("sounds\\"))){ins.Operand=s.Replace('\\','/');paths++;}
   }
  }
  if(mod.Assembly.Name.Name=="AngelModeUnlockMod"){
   var method=mod.Types.SelectMany(t=>t.Methods).Single(m=>m.Name=="DumpTabs"&&m.Parameters.Count==0);
   method.Body=new MethodBody(method);var il=method.Body.GetILProcessor();il.Append(il.Create(OpCodes.Call,Find("CesiumLoader.SDK.Mobile.MobileCompatibility","DumpSettingsTabs",0)));il.Append(il.Create(OpCodes.Ret));
  }
  if(mod.Assembly.Name.Name=="FreeCameraMod"){
   var type=mod.Types.Single(t=>t.Name=="ZoomCameraController");
   var init=type.Methods.Single(m=>m.Name=="OnInitialize");var il=init.Body.GetILProcessor();var first=init.Body.Instructions[0];
   il.InsertBefore(first,il.Create(OpCodes.Ldarg_0));il.InsertBefore(first,il.Create(OpCodes.Call,Find("CesiumLoader.SDK.Mobile.MobileTouch","AttachCamera",1)));
   var update=type.Methods.Single(m=>m.Name=="OnUpdate");il=update.Body.GetILProcessor();first=update.Body.Instructions[0];
   var enter=il.Create(OpCodes.Nop);
   il.InsertBefore(first,il.Create(OpCodes.Ldarg_0));il.InsertBefore(first,il.Create(OpCodes.Call,Find("CesiumLoader.SDK.Mobile.MobileTouch","UpdateCamera",1)));il.InsertBefore(first,il.Create(OpCodes.Brfalse,enter));il.InsertBefore(first,il.Create(OpCodes.Ret));il.InsertBefore(first,enter);
  }
  if(mod.Assembly.Name.Name=="TurnFocusMod"){
   var method=mod.Types.SelectMany(t=>t.Methods).Single(m=>m.Name=="RequestFocus"&&m.Parameters.Count==1);
   method.Body=new MethodBody(method);var il=method.Body.GetILProcessor();il.Append(il.Create(OpCodes.Ldarg_1));il.Append(il.Create(OpCodes.Call,Find("CesiumLoader.SDK.Mobile.MobileNotice","Show",1)));il.Append(il.Create(OpCodes.Ret));
  }
  Directory.CreateDirectory(Path.GetDirectoryName(args[1]));mod.Write(args[1]);
  Console.WriteLine(mod.Assembly.Name.Name+" env="+env+" paths="+paths);
 }
 static IEnumerable<TypeDefinition> All(IEnumerable<TypeDefinition> ts){foreach(var t in ts){yield return t;foreach(var n in All(t.NestedTypes))yield return n;}}
}
