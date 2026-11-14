from pathlib import Path
import urllib.request,zipfile,concurrent.futures,json,hashlib
p=Path(__file__).resolve().parent/'tools';p.mkdir(parents=True,exist_ok=True)
items={
'platform-tools':('https://dl.google.com/android/repository/platform-tools_r37.0.3-win.zip','zip'),
'platform35':('https://dl.google.com/android/repository/platform-35_r02.zip','zip'),
'build-tools':('https://dl.google.com/android/repository/build-tools_r35_windows.zip','zip'),
'csc':('https://api.nuget.org/v3-flatcontainer/microsoft.net.compilers.toolset/4.12.0/microsoft.net.compilers.toolset.4.12.0.nupkg','zip'),
'netstandard':('https://api.nuget.org/v3-flatcontainer/netstandard.library/2.0.3/netstandard.library.2.0.3.nupkg','zip'),
'net8ref':('https://api.nuget.org/v3-flatcontainer/microsoft.netcore.app.ref/8.0.28/microsoft.netcore.app.ref.8.0.28.nupkg','zip'),
'cecil':('https://api.nuget.org/v3-flatcontainer/mono.cecil/0.11.6/mono.cecil.0.11.6.nupkg','zip'),
'shizuku-api':('https://repo.maven.apache.org/maven2/dev/rikka/shizuku/api/13.1.5/api-13.1.5.aar','aar'),
'shizuku-provider':('https://repo.maven.apache.org/maven2/dev/rikka/shizuku/provider/13.1.5/provider-13.1.5.aar','aar'),
'shizuku-aidl':('https://repo.maven.apache.org/maven2/dev/rikka/shizuku/aidl/13.1.5/aidl-13.1.5.aar','aar'),
'shizuku-shared':('https://repo.maven.apache.org/maven2/dev/rikka/shizuku/shared/13.1.5/shared-13.1.5.aar','aar'),
'androidx-annotation':('https://dl.google.com/dl/android/maven2/androidx/annotation/annotation-jvm/1.9.1/annotation-jvm-1.9.1.jar','jar')}
def get(it):
 name,(u,typ)=it;f=p/(name+'.'+typ)
 if not f.exists():urllib.request.urlretrieve(u,f)
 if typ in ['zip','aar']:
  out=p/name;out.mkdir(exist_ok=True)
  with zipfile.ZipFile(f) as z:z.extractall(out)
 return name+' ready '+str(f.stat().st_size)
failed=[]
with concurrent.futures.ThreadPoolExecutor(max_workers=6) as ex:
 for f in concurrent.futures.as_completed([ex.submit(get,it) for it in items.items()]):
  try:print(f.result(),flush=True)
  except Exception as e:
   failed.append(e);print(type(e).__name__,str(e),flush=True)
if failed:raise SystemExit('Dependency download failed; retry download-tools.py before building.')
