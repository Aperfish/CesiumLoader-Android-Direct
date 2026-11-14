using System;
using System.IO;
using System.Linq;
using Mono.Cecil;
using Mono.Cecil.Cil;

internal static class PatchRuntime
{
    private static int Main(string[] args)
    {
        if (args.Length == 3 && args[0] == "--zip-bootstrap") return PatchZipBootstrap(args[1], args[2]);
        if (args.Length != 3) throw new ArgumentException("original.dll bootstrap.dll output.dll");
        var resolver = new DefaultAssemblyResolver();
        resolver.AddSearchDirectory(Path.GetDirectoryName(Path.GetFullPath(args[0]))!);
        using var module = ModuleDefinition.ReadModule(args[0], new ReaderParameters { AssemblyResolver = resolver });
        using var bootstrap = ModuleDefinition.ReadModule(args[1], new ReaderParameters { AssemblyResolver = resolver });
        if (module.Types.Any(t => t.Namespace.StartsWith("ModLoader.Android") || t.Namespace == "CesiumLoader.Android"))
            throw new Exception("Input already contains a loader; use untouched matching original");

        var entry = bootstrap.Types.Single(t => t.FullName == "CesiumLoader.Android.Entry");
        MethodReference Find(string method, string declaring, string name)
        {
            return module.ImportReference(entry.Methods.Single(m => m.Name == method).Body.Instructions
                .Select(i => i.Operand).OfType<MethodReference>()
                .First(m => m.DeclaringType.FullName == declaring && m.Name == name));
        }
        var readBytes = Find("LoadAssembly", "System.IO.File", "ReadAllBytes");
        var load = Find("LoadAssembly", "System.Reflection.Assembly", "Load");
        var getType = Find("LoadMod", "System.Reflection.Assembly", "GetType");
        var getMethod = Find("LoadMod", "System.Type", "GetMethod");
        var invoke = entry.Methods.Single(m => m.Name == "LoadMod").Body.Instructions
            .Select(i => i.Operand).OfType<MethodReference>().First(m => m.Name == "Invoke");
        invoke = module.ImportReference(invoke);
        var dataPath = Find("Initialize", "UnityEngine.Application", "get_persistentDataPath");
        var combine = entry.Methods.Single(m => m.Name == "Initialize").Body.Instructions
            .Select(i => i.Operand).OfType<MethodReference>()
            .First(m => m.DeclaringType.FullName == "System.IO.Path" && m.Name == "Combine" && m.Parameters.Count == 2);
        combine = module.ImportReference(combine);
        var debug = new TypeReference("UnityEngine", "Debug", module, module.AssemblyReferences.Single(a => a.Name == "UnityEngine.CoreModule"));
        var logError = new MethodReference("LogError", module.TypeSystem.Void, debug);
        logError.Parameters.Add(new ParameterDefinition(module.TypeSystem.Object));
        var exceptionType = new TypeReference("System", "Exception", module, module.TypeSystem.CoreLibrary);

        var bridge = new TypeDefinition("CesiumLoader.Android", "BootstrapBridge", TypeAttributes.Public | TypeAttributes.Abstract | TypeAttributes.Sealed, module.TypeSystem.Object);
        module.Types.Add(bridge);
        var started = new FieldDefinition("started", FieldAttributes.Private | FieldAttributes.Static, module.TypeSystem.Boolean);
        bridge.Fields.Add(started);
        var tick = new MethodDefinition("Tick", MethodAttributes.Public | MethodAttributes.Static, module.TypeSystem.Void);
        bridge.Methods.Add(tick);
        tick.Body.InitLocals = true;
        var error = new VariableDefinition(exceptionType);
        tick.Body.Variables.Add(error);
        var il = tick.Body.GetILProcessor();
        var end = Instruction.Create(OpCodes.Ret);
        il.Emit(OpCodes.Ldsfld, started);
        il.Emit(OpCodes.Brtrue, end);
        il.Emit(OpCodes.Ldc_I4_1);
        il.Emit(OpCodes.Stsfld, started);
        var startTry = Instruction.Create(OpCodes.Call, dataPath);
        il.Append(startTry);
        il.Emit(OpCodes.Ldstr, "CesiumLoader/bootstrap/CesiumLoader.Android.Bootstrap.dll");
        il.Emit(OpCodes.Call, combine);
        il.Emit(OpCodes.Call, readBytes);
        il.Emit(OpCodes.Call, load);
        il.Emit(OpCodes.Ldstr, "CesiumLoader.Android.Entry");
        il.Emit(OpCodes.Callvirt, getType);
        il.Emit(OpCodes.Ldstr, "Initialize");
        il.Emit(OpCodes.Callvirt, getMethod);
        il.Emit(OpCodes.Ldnull);
        il.Emit(OpCodes.Ldnull);
        il.Emit(OpCodes.Callvirt, invoke);
        il.Emit(OpCodes.Pop);
        il.Emit(OpCodes.Leave, end);
        var handler = Instruction.Create(OpCodes.Stloc, error);
        il.Append(handler);
        il.Emit(OpCodes.Ldloc, error);
        il.Emit(OpCodes.Call, logError);
        il.Emit(OpCodes.Leave, end);
        il.Append(end);
        tick.Body.ExceptionHandlers.Add(new ExceptionHandler(ExceptionHandlerType.Catch)
        { CatchType = exceptionType, TryStart = startTry, TryEnd = handler, HandlerStart = handler, HandlerEnd = end });

        var login = module.Types.Single(t => t.FullName == "Core.Scene.LoginSceneController");
        var awake = login.Methods.Single(m => m.Name == "Awake" && m.HasBody);
        awake.Body.GetILProcessor().InsertBefore(awake.Body.Instructions[0], Instruction.Create(OpCodes.Call, tick));
        module.Write(args[2]);
        using var check = ModuleDefinition.ReadModule(args[2]);
        var patchedAwake = check.Types.Single(t => t.FullName == login.FullName).Methods.Single(m => m.Name == "Awake");
        if (patchedAwake.Body.Instructions[0].Operand is not MethodReference first || first.FullName != tick.FullName)
            throw new Exception("Post-write injection verification failed");
        Console.WriteLine("Patched and verified " + login.FullName + ".Awake; original instructions retained");
        return 0;
    }
    private static int PatchZipBootstrap(string input, string output)
    {
        using var m = ModuleDefinition.ReadModule(input);
        var unity = new AssemblyNameReference("UnityEngine.CoreModule", new Version(0,0,0,0));
        m.AssemblyReferences.Add(unity);
        var app = new TypeReference("UnityEngine", "Application", m, unity);
        var getter = new MethodReference("get_persistentDataPath", m.TypeSystem.String, app);
        var path = new TypeReference("System.IO", "Path", m, m.AssemblyReferences.Single(a => a.Name == "netstandard"));
        var combine = new MethodReference("Combine", m.TypeSystem.String, path);
        combine.Parameters.Add(new ParameterDefinition(m.TypeSystem.String));
        combine.Parameters.Add(new ParameterDefinition(m.TypeSystem.String));
        int changed = 0;
        foreach(var t in m.Types)
        foreach(var method in t.Methods.Where(x => x.HasBody))
        {
            var il = method.Body.GetILProcessor();
            foreach(var i in method.Body.Instructions.ToArray())
            {
                if(i.OpCode != OpCodes.Ldstr || i.Operand is not string value) continue;
                string? relative = value == "/sdcard/CesiumLoader" ? "CesiumLoader" :
                    value.StartsWith("/sdcard/CesiumLoader/") ? "CesiumLoader/" + value.Substring("/sdcard/CesiumLoader/".Length) :
                    value.StartsWith("/sdcard/Android/data/com.feimo.astralparty/files/CesiumLoader/") ?
                    "CesiumLoader/logs/" + value.Substring(value.LastIndexOf('/')+1) : null;
                if(relative == null) continue;
                i.OpCode = OpCodes.Call; i.Operand = getter;
                var name = Instruction.Create(OpCodes.Ldstr, relative);
                il.InsertAfter(i,name); il.InsertAfter(name,Instruction.Create(OpCodes.Call,combine));
                changed++;
            }
        }
        if(changed < 2) throw new Exception("Unexpected ZIP bootstrap path layout");
        m.Write(output);
        Console.WriteLine("ZIP bootstrap: replaced " + changed + " fixed paths with game-owned storage");
        return 0;
    }

}
