from pathlib import Path
import subprocess,sys,os
r=Path(__file__).resolve().parents[1];t=Path(os.environ.get('CESIUM_BUILD_TOOLS',str(r/'tools')));b=r/'build/api-test';b.mkdir(parents=True,exist_ok=True)
refs=list((t/'net8ref/ref/net8.0').glob('*.dll'))
def compile(name,src,extra=(),exe=False):
 out=b/(name+'.dll');args=['-nologo','-nostdlib+','-target:exe' if exe else '-target:library','-out:'+str(out)]+['-r:'+str(x) for x in refs+list(extra)]+list(map(str,src));rsp=b/(name+'.rsp');rsp.write_text('\n'.join('"'+x+'"' for x in args),encoding='utf-8')
 subprocess.run(['dotnet',str(t/'csc/tasks/netcore/bincore/csc.dll'),'@'+str(rsp)],check=True)
 return out
unity=compile('UnityEngine.CoreModule',[r/'tests/ApiTestUnity.cs'])
api=compile('CesiumAndroidApi',[r/'src/CesiumAndroidApi/AndroidApi.cs'],[unity])
test=compile('ApiRegistryTest',[r/'tests/ApiRegistryTest.cs'],[unity,api],True)
(b/'ApiRegistryTest.runtimeconfig.json').write_text('{"runtimeOptions":{"tfm":"net8.0","framework":{"name":"Microsoft.NETCore.App","version":"8.0.0"}}}')
p=subprocess.run(['dotnet',str(test),str(b/'sandbox')],capture_output=True,text=True)
print(p.stdout.strip());print(p.stderr.strip());(b/'result.txt').write_text(p.stdout+p.stderr,encoding='utf-8');sys.exit(p.returncode)
