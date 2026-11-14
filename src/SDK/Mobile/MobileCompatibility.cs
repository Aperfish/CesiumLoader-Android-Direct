using System;
using System.Collections;
using System.Reflection;
using FairyGUI;
using UnityEngine;
namespace CesiumLoader.SDK.Mobile {
 public static class MobileCompatibility {
  static object Read(object value,string name){
   if(value==null)return null;var type=value.GetType();
   var p=type.GetProperty(name,BindingFlags.Public|BindingFlags.NonPublic|BindingFlags.Instance);
   return p!=null?p.GetValue(value,null):type.GetField(name,BindingFlags.Public|BindingFlags.NonPublic|BindingFlags.Instance)?.GetValue(value);
  }
  public static int GalleryCardId(object item,object window){
   try{
    object current=Read(item,"CurrentCardId");if(current!=null)return Convert.ToInt32(current);
    var child=item as GObject;var list=child?.parent as GList;if(list==null)return 0;
    int index=list.ChildIndexToItemIndex(list.GetChildIndex(child));
    var content=Read(window,"contentPane");var label=Read(content,"label") as Controller;
    var rows=Read(window,label?.selectedIndex==5?"EventInfos":"CardInfos") as IList;
    if(rows==null||index<0||index>=rows.Count)return 0;
    return Convert.ToInt32(Read(rows[index],"Id")??0);
   }catch{return 0;}
  }
  public static void DumpSettingsTabs(){
   try{
    var settings=StaticConfigure.Settings;var rows=Read(settings,"SettingsTabs") as IEnumerable;
    if(rows==null){Debug.Log("[CesiumAndroid] 当前设置布局没有 SettingsTabs，保留原生页签。");return;}
    foreach(var row in rows)Debug.Log("[CesiumAndroid] settings tab="+Read(row,"SettingTabType")+" order="+Read(row,"ShowOrder"));
   }catch(Exception e){Debug.LogWarning("[CesiumAndroid] settings diagnostics: "+e.Message);}
  }
 }
}
