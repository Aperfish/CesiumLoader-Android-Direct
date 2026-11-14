using System.Collections.Generic;
using UnityEngine;
namespace CesiumLoader.SDK.Mobile {
 public static class VirtualKeys {
  static readonly Dictionary<KeyCode,int> frames=new Dictionary<KeyCode,int>();
  static readonly HashSet<KeyCode> latched=new HashSet<KeyCode>();
  public static void ToggleHeld(KeyCode key){if(!latched.Remove(key))latched.Add(key);}
  // Schedule on the next frame, so every mod sees the same press regardless of update ordering.
  public static void Pulse(KeyCode key){frames[key]=Time.frameCount+1;}
  public static bool Held(KeyCode key){int frame;return latched.Contains(key)||(frames.TryGetValue(key,out frame)&&Time.frameCount==frame);}
  public static bool Pressed(KeyCode key){int frame;return frames.TryGetValue(key,out frame)&&Time.frameCount==frame;}
  public static bool Released(KeyCode key){int frame;return frames.TryGetValue(key,out frame)&&Time.frameCount==frame+1;}
  public static void Clear(){frames.Clear();latched.Clear();}
 }
}
