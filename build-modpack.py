from pathlib import Path
import os,sys,json,hashlib,zipfile,subprocess,shutil
ROOT=Path(__file__).resolve().parent
TOOLS=Path(os.environ.get('CESIUM_BUILD_TOOLS',str(ROOT/'tools')))
BASE=ROOT
MODS=Path(os.environ.get('CESIUM_PC_MODS',str(ROOT/'inputs/mods')))
if not MODS.is_dir():raise SystemExit('Set CESIUM_PC_MODS to your local supported PC mods directory.')
BUILD=ROOT/'build';BUILD.mkdir(exist_ok=True)
OUT=BUILD/'payload-modpack';OUT.mkdir(exist_ok=True)
def run(args):
 r=subprocess.run(list(map(str,args)),capture_output=True,text=True,encoding='utf-8',errors='replace')
 if r.returncode:raise RuntimeError(r.stdout+r.stderr)
 return r.stdout.strip()
def sha(b):return hashlib.sha256(b).hexdigest()
def compile(name,sources,refs,output,exe=False):
 output.parent.mkdir(parents=True,exist_ok=True)
 rsp=output.with_suffix('.rsp')
 args=['-nologo','-nostdlib+','-unsafe+','-langversion:latest','-deterministic+','-target:exe' if exe else '-target:library','-out:'+str(output)]+['-r:'+str(r) for r in refs]+list(map(str,sources))
 rsp.write_text('\n'.join('"'+a+'"' for a in args),encoding='utf-8')
 log=run(['dotnet',TOOLS/'csc/tasks/netcore/bincore/csc.dll','@'+str(rsp)])
 output.with_suffix('.build.log').write_text(log,encoding='utf-8')
 print('compiled',name,flush=True)
std=list((TOOLS/'netstandard/build/netstandard2.0/ref').glob('*.dll'))
net8=list((TOOLS/'net8ref/ref/net8.0').glob('*.dll'))
cecil=TOOLS/'cecil/lib/netstandard2.0/Mono.Cecil.dll'
compile('PortMods',[ROOT/'src/PortMods.cs'],net8+[cecil],BUILD/'PortMods.dll',True)
compile('PatchRuntime',[ROOT/'src/PatchRuntime.cs'],net8+[cecil],BUILD/'PatchRuntime.dll',True)
shutil.copy2(cecil,BUILD/'Mono.Cecil.dll')
(BUILD/'PortMods.runtimeconfig.json').write_text('{"runtimeOptions":{"tfm":"net8.0","framework":{"name":"Microsoft.NETCore.App","version":"8.0.0"}}}')
(BUILD/'PatchRuntime.runtimeconfig.json').write_text((BUILD/'PortMods.runtimeconfig.json').read_text())
with zipfile.ZipFile(BASE/'android/assets/payload.zip') as z:z.extractall(OUT)
manifest=json.loads((OUT/'manifest.json').read_text(encoding='utf-8'))
manifest['version']='0.4.0';manifest['sdkVersion']='3.7.5-android-mobile';manifest['mods']=[]
names={'ActivityLogMod':'实时行为日志','AngelModeUnlockMod':'天使模式解锁','AudioSkinMod':'音效替换（需音效方案）','AutoThanksMod':'自动感谢','BattleCardPointsMod':'战斗卡牌点数','CardSkinMod':'卡牌皮肤（需皮肤素材）','CharacterSkinMod':'角色皮肤（需皮肤素材）','CharacterTransparencyMod':'角色半透明（触屏）','ChatDurationMod':'消息停留时间','CombatOddsMod':'战斗胜率助手','ForkDirectionMod':'岔路口看牌','FreeCameraMod':'自由相机（双指滑动）','HandViewerMod':'手牌查看','HideHudMod':'隐藏 HUD（触屏）','HomeBottomBarMod':'首页底栏展开','LanguageUnlockMod':'多语言支持','MapPeekMod':'选人地图预览','PinnedRelicsMod':'关键筹码置顶','PvePanelMod':'PVE 面板','PveWaveForecastMod':'PVE 刷怪预报','QuickChatPlusMod':'快捷短语扩展','ShopRelicPeekMod':'商店与筹码看板','SpeedHackMod':'变速悬浮面板（Unity）','StarCoinShopMod':'星币商店助手','TurnClockMod':'回合时钟','TurnFocusMod':'回合提醒（游戏内）','UnlockFrameRateMod':'帧率解锁','NoBotMod':'不自动补人机','AndroidControlsMod':'安卓触屏控制入口'}
names['CesiumOverlayMod']='Cesium 安卓悬浮窗'
for id,name in names.items():manifest['mods'].append({'id':id,'name':name})
generated='using System.Collections.Generic; namespace AndroidControlsMod { internal static class ModNames { internal static readonly Dictionary<string,string> All = new Dictionary<string,string> {\n'
generated+=',\n'.join('{'+json.dumps(id,ensure_ascii=False)+','+json.dumps(name,ensure_ascii=False)+'}' for id,name in names.items())
generated+='}; } }'
(ROOT/'src/AndroidControlsMod/ModNames.cs').write_text(generated,encoding='utf-8')
common=OUT/'common'
shutil.copy2(ROOT/'assets/overlay.png',common/'overlay.png')
for license in (ROOT/'licenses').glob('*'):
 if license.is_file():shutil.copy2(license,common/'licenses'/license.name)
with zipfile.ZipFile(common/'original-input.zip') as z:
 raw_bootstrap=z.read(next(n for n in z.namelist() if n.endswith('/CesiumBootstrap.dll')))
(BUILD/'CesiumBootstrap.original.dll').write_bytes(raw_bootstrap)
patched=[]
for n,profile in enumerate(manifest['profiles']):
 id=profile['id'];dest=OUT/'profiles'/id
 refs=BASE/'build'/id/'refs'
 game=[refs/x for x in ('AstralParty.Runtime.dll','UnityEngine.CoreModule.dll','UniTask.dll','Google.Protobuf.Runtime.dll')]
 sdk=dest/'CesiumLoader.SDK.dll'
 compile('SDK '+id,list((ROOT/'src/SDK').rglob('*.cs')),std+game,sdk)
 api=dest/'CesiumAndroidApi.dll'
 compile('Android API '+id,list((ROOT/'src/CesiumAndroidApi').rglob('*.cs')),std+[refs/'UnityEngine.CoreModule.dll'],api)
 profile['files']['CesiumAndroidApi.dll']=sha(api.read_bytes())
 compile('Wrapper '+id,[ROOT/'src/Bootstrap.cs'],std+game,dest/'CesiumLoader.Android.Bootstrap.dll')
 print(run(['dotnet',BUILD/'PatchRuntime.dll','--zip-bootstrap',BUILD/'CesiumBootstrap.original.dll',dest/'CesiumBootstrap.dll']),flush=True)
 for name in ('CesiumLoader.Android.Bootstrap.dll','CesiumBootstrap.dll'):profile['files'][name]=sha((dest/name).read_bytes())
 for mod in ('SpeedHackMod','AndroidControlsMod','CharacterTransparencyMod','CesiumOverlayMod'):
  target=dest/'mods'/(mod+'.dll')
  compile(mod+' '+id,list((ROOT/'src'/mod).rglob('*.cs')),std+game+[sdk,api],target)
  profile['files']['mods/'+mod+'.dll']=sha(target.read_bytes())
 profile['files']['CesiumLoader.SDK.dll']=sha(sdk.read_bytes())
 if n==0:
  for mod in MODS.iterdir():
   dll=mod/(mod.name+'.dll')
   if not dll.exists() or mod.name=='SpeedHackMod':continue
   target=common/'mods'/dll.name
   print(run(['dotnet',BUILD/'PortMods.dll',dll,target,sdk,refs]),flush=True)
   patched.append(mod.name)
   # Keep data, icons and notification sounds. Do not import desktop logs or transient state.
   for folder in ('data','sounds','schemes'):
    source=mod/folder
    if source.is_dir():shutil.copytree(source,common/'mods'/mod.name/folder,dirs_exist_ok=True)
   cfg=common/'mods'/mod.name/'config.json';cfg.parent.mkdir(parents=True,exist_ok=True)
   source=mod/'config.json'
   config=json.loads(source.read_text(encoding='utf-8-sig')) if source.exists() else {}
   config.pop('_comments',None)
   if mod.name=='HideHudMod':config['pressMode']='Toggle'
   if mod.name=='CharacterTransparencyMod':config['holdMode']=False
   if mod.name=='PveWaveForecastMod':config['enabled']=True
   if mod.name=='CardSkinMod':config['ActiveSkin']='default'
   if mod.name=='TurnFocusMod':config.update(soundEnabled=False)
   cfg.write_text(json.dumps(config,ensure_ascii=False,indent=2),encoding='utf-8')
assert len(patched)==26
for mod in ('SpeedHackMod','AndroidControlsMod','CesiumOverlayMod'):
 cfg=common/'mods'/mod/'config.json';cfg.parent.mkdir(parents=True,exist_ok=True)
 cfg.write_text(json.dumps({'speed':2,'maxSpeed':10,'speedStep':.5,'accelerationMode':'unity','panelCollapsed':False} if mod=='SpeedHackMod' else {},indent=2),encoding='utf-8')
old=common/'mods/SpeedHackMod.dll'
if old.exists():old.unlink()
old=common/'mods/CharacterTransparencyMod.dll'
if old.exists():old.unlink()
manifest['commonFiles']={p.relative_to(common).as_posix():sha(p.read_bytes()) for p in common.rglob('*') if p.is_file()}
(OUT/'manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf-8')
(ROOT/'android/assets/mod-list.json').write_text(json.dumps(manifest['mods'],ensure_ascii=False),encoding='utf-8')
with zipfile.ZipFile(ROOT/'android/assets/payload.zip','w',zipfile.ZIP_DEFLATED) as z:
 for p in OUT.rglob('*'):
  if p.is_file() and p.suffix not in ('.rsp','.log'):z.write(p,p.relative_to(OUT).as_posix())
print('PAYLOAD',len(manifest['mods']),'mods; 4 profiles;', (ROOT/'android/assets/payload.zip').stat().st_size,'bytes',flush=True)
