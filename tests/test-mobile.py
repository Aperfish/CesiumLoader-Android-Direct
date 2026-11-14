from pathlib import Path
import os, subprocess, sys
root=Path(__file__).resolve().parents[1]
tools=Path(os.environ.get('CESIUM_BUILD_TOOLS',str(root/'tools')))
out=root/'build/mobile-test';out.mkdir(parents=True,exist_ok=True)
sources=[root/'tests/MobileLogicTest.cs',root/'tests/ApiTestUnity.cs',root/'src/SDK/Mobile/GestureState.cs',
         root/'src/SpeedHackMod/UnitySpeedScale.cs',root/'src/SpeedHackMod/FixedStepBudget.cs']
refs=list((tools/'net8ref/ref/net8.0').glob('*.dll'))
args=['-nologo','-nostdlib+','-target:exe','-out:'+str(out/'MobileLogicTest.dll')]+['-r:'+str(p) for p in refs]+list(map(str,sources))
rsp=out/'compile.rsp';rsp.write_text('\n'.join('"'+a+'"' for a in args),encoding='utf-8')
subprocess.run(['dotnet',str(tools/'csc/tasks/netcore/bincore/csc.dll'),'@'+str(rsp)],check=True)
(out/'MobileLogicTest.runtimeconfig.json').write_text('{"runtimeOptions":{"tfm":"net8.0","framework":{"name":"Microsoft.NETCore.App","version":"8.0.0"}}}')
result=subprocess.run(['dotnet',str(out/'MobileLogicTest.dll')],capture_output=True,text=True)
print(result.stdout.strip());print(result.stderr.strip())
(out/'result.txt').write_text(result.stdout+result.stderr,encoding='utf-8');sys.exit(result.returncode)
