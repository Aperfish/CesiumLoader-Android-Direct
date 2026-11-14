from pathlib import Path
import subprocess,json,sys,os
root=Path(__file__).resolve().parents[1];tools=Path(os.environ.get('CESIUM_BUILD_TOOLS',str(root/'tools')));out=root/'build/pump-test';out.mkdir(parents=True,exist_ok=True)
sources=[root/'tests/PumpRegressionTest.cs']+[root/'src/SDK'/x for x in ('Runtime/MainThread.cs','Runtime/MainThreadDispatcher.cs','Scheduling/UpdateService.cs','Scheduling/UpdateSubscription.cs')]
args=['-nologo','-nostdlib+','-target:exe','-out:'+str(out/'PumpTest.dll')]+['-r:'+str(p) for p in (tools/'net8ref/ref/net8.0').glob('*.dll')]+list(map(str,sources))
rsp=out/'compile.rsp';rsp.write_text('\n'.join('"'+a+'"' for a in args),encoding='utf-8')
subprocess.run(['dotnet',str(tools/'csc/tasks/netcore/bincore/csc.dll'),'@'+str(rsp)],check=True)
(out/'PumpTest.runtimeconfig.json').write_text('{"runtimeOptions":{"tfm":"net8.0","framework":{"name":"Microsoft.NETCore.App","version":"8.0.0"}}}')
r=subprocess.run(['dotnet',str(out/'PumpTest.dll')]+sys.argv[1:],capture_output=True,text=True)
print(r.stdout.strip());print(r.stderr.strip())
(out/('result-'+('green' if r.returncode==0 else 'red')+'.txt')).write_text(r.stdout+r.stderr,encoding='utf-8')
sys.exit(r.returncode)
