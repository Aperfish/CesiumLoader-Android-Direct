from pathlib import Path
import sys, os, subprocess, hashlib, json, zipfile, shutil, bz2, zlib, struct

ROOT=Path(__file__).resolve().parent
_default_tools=ROOT/'tools'
TOOLS=Path(os.environ.get('CESIUM_BUILD_TOOLS',str(_default_tools)))
_default_libs=ROOT/'python-libs'
LIBS=Path(os.environ.get('CESIUM_PYTHON_LIBS',str(_default_libs)))
sys.path.insert(0,str(LIBS))
import UnityPy,bsdiff4
OUT=ROOT/'build';OUT.mkdir(exist_ok=True)
PAYLOAD=OUT/'payload';PAYLOAD.mkdir(exist_ok=True)
CSC=TOOLS/'csc/tasks/netcore/bincore/csc.dll'
STD=list((TOOLS/'netstandard/build/netstandard2.0/ref').glob('*.dll'))
NET8=list((TOOLS/'net8ref/ref/net8.0').glob('*.dll'))
def sha(b):return hashlib.sha256(b).hexdigest()
def compile(name,sources,refs,out,exe=False):
    out.parent.mkdir(parents=True,exist_ok=True)
    args=['-nologo','-nostdlib+','-unsafe+','-langversion:latest','-deterministic+', '-target:exe' if exe else '-target:library','-out:'+str(out)]
    args+=['-r:'+str(r) for r in refs]+[str(s) for s in sources]
    rsp=out.with_suffix('.rsp');rsp.write_text('\n'.join('"'+a+'"' for a in args),encoding='utf-8')
    r=subprocess.run(['dotnet',str(CSC),'@'+str(rsp)],capture_output=True,text=True,encoding='utf-8')
    out.with_suffix('.build.log').write_text(r.stdout+r.stderr,encoding='utf-8')
    if r.returncode:raise RuntimeError(name+': '+(r.stdout+r.stderr)[-9000:])
    print('compiled',name,flush=True)
cecil=TOOLS/'cecil/lib/netstandard2.0/Mono.Cecil.dll'
patcher=OUT/'PatchRuntime.dll'
compile('Patcher',[ROOT/'src/PatchRuntime.cs'],NET8+[cecil],patcher,True)
shutil.copy2(cecil,OUT/'Mono.Cecil.dll')
(OUT/'PatchRuntime.runtimeconfig.json').write_text('{"runtimeOptions":{"tfm":"net8.0","framework":{"name":"Microsoft.NETCore.App","version":"8.0.0"}}}')
def patch(args):
    p=subprocess.run(['dotnet',str(patcher)]+list(map(str,args)),capture_output=True,text=True)
    if p.returncode:raise RuntimeError(p.stdout+p.stderr)
    print(p.stdout.strip(),flush=True)

inputs=OUT/'input';inputs.mkdir(exist_ok=True)
with zipfile.ZipFile(ROOT/'fixtures/CesiumLoader-input.zip') as z:
    for name in z.namelist():
        if name.endswith('.dll'):
            dest=inputs/Path(name).name;dest.write_bytes(z.read(name))
manifest={'format':2,'version':'0.2.0','bundleKey':'4da1f23c112208b0f8df2a1327b4e4ce','profiles':[],
          'inputZipSha256':sha((ROOT/'fixtures/CesiumLoader-input.zip').read_bytes()),'sdkVersion':'3.7.5-android',
          'mods':[{'id':'CombatOddsMod','name':'战斗胜率助手'},{'id':'LanguageUnlockMod','name':'语言解锁'},
                  {'id':'MapPeekMod','name':'地图预览'},{'id':'NoBotMod','name':'不自动补人机'}], 'commonFiles':{}}
common=PAYLOAD/'common';common.mkdir(exist_ok=True)
for mod in manifest['mods']:
    dest=common/'mods'/ (mod['id']+'.dll');dest.parent.mkdir(exist_ok=True);shutil.copy2(inputs/(mod['id']+'.dll'),dest)
    # Separate per-mod configuration folders are used by the adapted SDK even though the DLLs are flat.
    cfg=common/'mods'/mod['id']/'config.json';cfg.parent.mkdir(exist_ok=True);cfg.write_text('{}\n')
shutil.copy2(ROOT/'fixtures/CesiumLoader-input.zip',common/'original-input.zip')
notice=common/'licenses';notice.mkdir(exist_ok=True)
for p in (ROOT/'licenses').glob('*'):
    if p.is_file():shutil.copy2(p,notice/p.name)
sdk_license=ROOT/'src/SDK/LICENSE'
if sdk_license.exists():shutil.copy2(sdk_license,notice/'CesiumLoader.SDK-MIT.txt')

sdk_sources=list((ROOT/'src/SDK').rglob('*.cs'))
for original in sorted((ROOT/'fixtures').glob('original-*.bundle')):
    pid=original.stem.split('-')[-1];work=OUT/pid;work.mkdir(exist_ok=True)
    refs=work/'refs';refs.mkdir(exist_ok=True)
    env=UnityPy.load(str(original));before={}
    for obj in env.objects:
        if obj.type.name=='TextAsset':
            a=obj.read();raw=a.m_Script.encode('utf-8','surrogateescape') if isinstance(a.m_Script,str) else bytes(a.m_Script)
            (refs/Path(a.m_Name).name).write_bytes(raw);before[a.m_Name]=sha(raw)
    game=[refs/n for n in ['AstralParty.Runtime.dll','UnityEngine.CoreModule.dll','UniTask.dll','Google.Protobuf.Runtime.dll']]
    profile=PAYLOAD/'profiles'/pid;profile.mkdir(parents=True,exist_ok=True)
    try:
        compile('SDK '+pid,sdk_sources,STD+game,profile/'CesiumLoader.SDK.dll')
        compile('Bootstrap '+pid,[ROOT/'src/Bootstrap.cs'],STD+game,profile/'CesiumLoader.Android.Bootstrap.dll')
        patch(['--zip-bootstrap',inputs/'CesiumBootstrap.dll',profile/'CesiumBootstrap.dll'])
        patched_runtime=work/'AstralParty.Runtime.patched.dll'
        patch([refs/'AstralParty.Runtime.dll',profile/'CesiumLoader.Android.Bootstrap.dll',patched_runtime])
        for obj in env.objects:
            if obj.type.name=='TextAsset':
                a=obj.read()
                if a.m_Name=='AstralParty.Runtime.dll':
                    a.m_Script=patched_runtime.read_bytes().decode('utf-8','surrogateescape');a.save()
        new=env.file.save(packer='lz4');old=original.read_bytes()
        check=UnityPy.load(new)
        for obj in check.objects:
            if obj.type.name=='TextAsset':
                a=obj.read();raw=a.m_Script.encode('utf-8','surrogateescape') if isinstance(a.m_Script,str) else bytes(a.m_Script)
                assert sha(raw)==(sha(patched_runtime.read_bytes()) if a.m_Name=='AstralParty.Runtime.dll' else before[a.m_Name])
        bsd=bsdiff4.diff(old,new);assert bsdiff4.patch(old,bsd)==new
        def off(x):
            n=int.from_bytes(x,'little');return -(n&0x7fffffffffffffff) if n>>63 else n
        csize,dsize=off(bsd[8:16]),off(bsd[16:24])
        ctrl=bz2.decompress(bsd[32:32+csize]);diff=bz2.decompress(bsd[32+csize:32+csize+dsize]);extra=bz2.decompress(bsd[32+csize+dsize:])
        delta=struct.pack('<8sQQIII',b'CSDIFF01',len(old),len(new),len(ctrl),len(diff),len(extra))+zlib.compress(ctrl+diff+extra,9)
        (profile/'cache.csdiff').write_bytes(delta)
        record={'id':pid,'gameVersion':'3.3.0','originalSha256':sha(old),'patchedSha256':sha(new),'originalBytes':len(old),'patchedBytes':len(new),'deltaSha256':sha(delta),
                'runtimeSha256':before['AstralParty.Runtime.dll'],'files':{},'legacyMinSha256':[],'legacyBackupRoot':'CesiumLoader.Android'}
        if pid=='0c6650c5e9eccffde61b8397d4694f72':record['legacyMinSha256']=['412f391ad50fbea31e003c8b4d62c55f3c5072b71257f279bb9abc5afbbcb6e5']
        for p in profile.iterdir():
            if p.suffix=='.dll':record['files'][p.name]=sha(p.read_bytes())
        manifest['profiles'].append(record)
        print('PROFILE',pid,'delta bytes',len(delta),'verified',flush=True)
    except Exception as e:
        print('PROFILE REJECTED',pid,str(e),flush=True)
if not manifest['profiles']:raise RuntimeError('No compatible game profiles built')
for p in common.rglob('*'):
    if p.is_file():manifest['commonFiles'][p.relative_to(common).as_posix()]=sha(p.read_bytes())
(PAYLOAD/'manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf-8')
assets=ROOT/'android/assets';assets.mkdir(parents=True,exist_ok=True)
with zipfile.ZipFile(assets/'payload.zip','w',zipfile.ZIP_DEFLATED) as z:
    z.write(PAYLOAD/'manifest.json','manifest.json')
    for p in PAYLOAD.rglob('*'):
        if p.is_file() and p.suffix in ['.dll','.csdiff','.txt','.json','.zip'] and p.name!='manifest.json':z.write(p,p.relative_to(PAYLOAD).as_posix())
print('PAYLOAD',len(manifest['profiles']),'profiles, bytes',(assets/'payload.zip').stat().st_size,flush=True)
