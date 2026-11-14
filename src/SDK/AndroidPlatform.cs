using System;
using System.IO;
using System.Reflection;
using UnityEngine;
[assembly: AssemblyVersion("2.5.1.0")]
[assembly: AssemblyFileVersion("3.7.5.0")]
namespace CesiumLoader.SDK {
 public static class AndroidPlatform {
  public static bool IsAndroid { get { return true; } }
  public static string GetEnvironmentVariable(string name) {
   string root = Path.Combine(Application.persistentDataPath, "CesiumLoader");
   switch(name) {
    case "CESIUM_LOADER_DIR": return root;
    case "CESIUM_GAME_DIR": return Application.persistentDataPath;
    case "CESIUM_MODS_DIR": return Path.Combine(root, "mods");
    case "CESIUM_SDK_DIR": return Path.Combine(root, "sdk");
    case "CESIUM_LOG_DIR": return Path.Combine(root, "logs");
    case "CESIUM_ACTIVITY_LOG_FILE": return Path.Combine(root, "logs", "activity.log");
    case "CESIUM_ERROR_LOG_FILE": return Path.Combine(root, "logs", "errors.log");
    case "CESIUM_AUDIO_ENABLED": return "0";
   }
   try { return Environment.GetEnvironmentVariable(name); } catch { return null; }
  }
 }
}
