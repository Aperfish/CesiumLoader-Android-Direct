"""Recompile the Android SDK/touch controls while preserving an existing Mod payload."""
from pathlib import Path
import hashlib, json, os, subprocess, zipfile

ROOT = Path(__file__).resolve().parent
TOOLS = Path(os.environ.get('CESIUM_BUILD_TOOLS', str(ROOT/'tools')))
BUILD = ROOT/'build/mobile-update'
BUILD.mkdir(parents=True, exist_ok=True)
payload = ROOT/'android/assets/payload.zip'
with zipfile.ZipFile(payload) as archive:
    files = {n: archive.read(n) for n in archive.namelist()}
manifest = json.loads(files['manifest.json'])
std = list((TOOLS/'netstandard/build/netstandard2.0/ref').glob('*.dll'))

def compile(output, sources, refs):
    output.parent.mkdir(parents=True, exist_ok=True)
    args = ['-nologo', '-nostdlib+', '-unsafe+', '-langversion:latest', '-deterministic+', '-target:library', '-out:'+str(output)]
    args += ['-r:'+str(r) for r in refs] + [str(s) for s in sources]
    rsp = output.with_suffix('.rsp')
    rsp.write_text('\n'.join('"'+a+'"' for a in args), encoding='utf-8')
    result = subprocess.run(['dotnet', str(TOOLS/'csc/tasks/netcore/bincore/csc.dll'), '@'+str(rsp)], capture_output=True, text=True, encoding='utf-8')
    output.with_suffix('.build.log').write_text(result.stdout+result.stderr, encoding='utf-8')
    if result.returncode:
        raise RuntimeError(result.stdout+result.stderr)

for p in manifest['profiles']:
    refs = ROOT/'build'/p['id']/'refs'
    game = [refs/n for n in ('AstralParty.Runtime.dll', 'UnityEngine.CoreModule.dll', 'UniTask.dll', 'Google.Protobuf.Runtime.dll')]
    dest = BUILD/p['id']
    sdk, api = dest/'CesiumLoader.SDK.dll', dest/'CesiumAndroidApi.dll'
    compile(sdk, list((ROOT/'src/SDK').rglob('*.cs')), std+game)
    api.write_bytes(files['profiles/'+p['id']+'/CesiumAndroidApi.dll'])
    controls = dest/'mods/AndroidControlsMod.dll'
    compile(controls, list((ROOT/'src/AndroidControlsMod').rglob('*.cs')), std+game+[sdk, api])
    for name, path in (('CesiumLoader.SDK.dll', sdk), ('mods/AndroidControlsMod.dll', controls)):
        data = path.read_bytes()
        files['profiles/'+p['id']+'/'+name] = data
        p['files'][name] = hashlib.sha256(data).hexdigest()
    print('MOBILE SDK AND CONTROLS', p['id'], flush=True)
for mod in manifest['mods']:
    if mod['id'] == 'FreeCameraMod':
        mod['name'] = '自由相机（双指缩放）'
files['manifest.json'] = json.dumps(manifest, ensure_ascii=False, indent=2).encode('utf-8')
(ROOT/'android/assets/mod-list.json').write_text(json.dumps(manifest['mods'], ensure_ascii=False), encoding='utf-8')
temp = payload.with_suffix('.mobile.zip')
with zipfile.ZipFile(temp, 'w', zipfile.ZIP_DEFLATED) as archive:
    for name, data in files.items():
        archive.writestr(name, data)
temp.replace(payload)
print('Updated all four SDK/control variants; retained other payload files')
