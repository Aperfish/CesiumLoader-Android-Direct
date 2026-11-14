using System;
using System.IO;
using System.Linq;
using System.Collections.Generic;
using System.Text.Json;
using Mono.Cecil;
public static class AuditMods {
 public static void Main(string[] args) {
  var resolver=new DefaultAssemblyResolver();
  foreach(var p in args.Skip(1))resolver.AddSearchDirectory(p);
  foreach(var file in Directory.GetFiles(args[0],"*.dll",SearchOption.AllDirectories)) {
   using var module=ModuleDefinition.ReadModule(file,new ReaderParameters{AssemblyResolver=resolver});
   var failures=new List<string>();var native=new List<string>();var inputs=new HashSet<string>();
   foreach(var tr in module.GetTypeReferences())if(IsGame(tr))try{if(tr.Resolve()==null)failures.Add("TYPE "+tr.FullName);}catch(Exception e){failures.Add("TYPE "+tr.FullName+" ["+e.GetType().Name+"]");}
   foreach(var type in All(module.Types))foreach(var method in type.Methods){
    if(method.IsPInvokeImpl)native.Add(method.PInvokeInfo.Module.Name+":"+method.PInvokeInfo.EntryPoint);
    if(!method.HasBody)continue;
    foreach(var i in method.Body.Instructions){
     if(i.Operand is MethodReference mr){
      if(mr.DeclaringType.FullName.Contains("Inputs.")||mr.DeclaringType.FullName=="UnityEngine.Input")inputs.Add(mr.FullName);
      if(IsGame(mr.DeclaringType))try{if(mr.Resolve()==null)failures.Add(mr.FullName);}catch(Exception e){failures.Add(mr.FullName+" ["+e.GetType().Name+"]");}
     }
     if(i.Operand is FieldReference fr && IsGame(fr.DeclaringType))try{if(fr.Resolve()==null)failures.Add(fr.FullName);}catch(Exception e){failures.Add(fr.FullName+" ["+e.GetType().Name+"]");}
    }
   }
   Console.WriteLine(JsonSerializer.Serialize(new{file,name=module.Assembly.Name.Name,refs=module.AssemblyReferences.Select(x=>x.Name).ToArray(),failures=failures.Distinct().ToArray(),native,inputs}));
  }
 }
 static bool IsGame(TypeReference t){var n=t.Scope?.Name??"";return n.StartsWith("AstralParty")||n.StartsWith("CesiumLoader")||n.StartsWith("CesiumAndroid")||n.StartsWith("Unity")||n.StartsWith("Google.Protobuf")||n.StartsWith("UniTask");}
 static IEnumerable<TypeDefinition> All(IEnumerable<TypeDefinition> ts){foreach(var t in ts){yield return t;foreach(var n in All(t.NestedTypes))yield return n;}}
}
