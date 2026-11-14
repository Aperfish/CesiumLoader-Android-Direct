using System;
using System.IO;
using System.Reflection;
using UnityEngine;

namespace CesiumLoader.Android
{
    // Intentionally independent of CesiumLoader.SDK: it is loaded by this entry.
    public static class Entry
    {
        private static bool started;
        private static string root;
        private static string log;

        public static void Initialize()
        {
            if (started) return;
            started = true;
            try
            {
                root = Path.Combine(Application.persistentDataPath, "CesiumLoader");
                Directory.CreateDirectory(Path.Combine(root, "logs"));
                log = Path.Combine(root, "logs", "loader.log");
                Write("BOOTSTRAP_ENTER build=modpack-0.4.0");
                if (File.Exists(Path.Combine(root, "DISABLED")))
                {
                    Write("DISABLED marker present");
                    return;
                }
                LoadAssembly(Path.Combine(root, "CesiumAndroidApi.dll"));
                Write("ANDROID_API_LOADED");
                Assembly zipBootstrap = LoadAssembly(Path.Combine(root, "CesiumBootstrap.dll"));
                Type zipEntry = zipBootstrap.GetType("CesiumBootstrap.Entry");
                if (zipEntry == null) throw new Exception("ZIP bootstrap entry missing");
                zipEntry.GetMethod("Start").Invoke(null, null);
                Write("ZIP_BOOTSTRAP_RETURNED");
                Write("BOOTSTRAP_FINISHED");
            }
            catch (Exception ex) { Write("BOOTSTRAP_ERROR " + Unwrap(ex)); }
        }

        private static Assembly LoadAssembly(string path)
        {
            return Assembly.Load(File.ReadAllBytes(path));
        }

        private static void LoadMod(string id)
        {
            try
            {
                Assembly asm = LoadAssembly(Path.Combine(root, "mods", id, id + ".dll"));
                Type entry = asm.GetType(id + ".ModEntry");
                if (entry == null) throw new Exception("Missing entry type: " + id);
                MethodInfo main = entry.GetMethod("Main");
                if (main == null) throw new Exception("Missing Main: " + id);
                main.Invoke(null, null);
                Write("MOD_ENTRY_RETURNED " + id);
            }
            catch (Exception ex) { Write("MOD_ERROR " + id + " " + Unwrap(ex)); }
        }

        private static Exception Unwrap(Exception ex)
        {
            while (ex is TargetInvocationException && ex.InnerException != null) ex = ex.InnerException;
            return ex;
        }

        private static void Write(string message)
        {
            try { Debug.Log("[CesiumAndroid] " + message); } catch { }
            try { if (log != null) File.AppendAllText(log, DateTime.UtcNow.ToString("o") + " " + message + "\n"); } catch { }
        }
    }
}
