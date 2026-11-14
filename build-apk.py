from pathlib import Path
import subprocess,os,sys,hashlib,zipfile,json,shutil,urllib.request

ROOT=Path(__file__).resolve().parent
_default_tools=ROOT/'tools'
TOOLS=Path(os.environ.get('CESIUM_BUILD_TOOLS',str(_default_tools)))
BUILD=ROOT/'build';BUILD.mkdir(exist_ok=True)
def run(args):
    p=subprocess.run([str(x) for x in args],capture_output=True,text=True,encoding='utf-8',errors='replace')
    if p.returncode:print(p.stdout,p.stderr);raise SystemExit(p.returncode)
    if p.stdout.strip():print(p.stdout.strip()[-3000:],flush=True)
    return p
payload=ROOT/'android/assets/payload.zip'
source=ROOT/'android/src/dev/cesium/androidmin/BuildInfo.java'
source.write_text('package dev.cesium.androidmin; public final class BuildInfo { public static final String PAYLOAD_SHA256="'+hashlib.sha256(payload.read_bytes()).hexdigest()+'"; }',encoding='utf-8')
bt=TOOLS/'build-tools/android-15';android=TOOLS/'platform35/android-35/android.jar'
jars=[TOOLS/('shizuku-'+n)/'classes.jar' for n in ['api','provider','aidl','shared']]+[TOOLS/'androidx-annotation.jar']
classes=BUILD/'java';classes.mkdir(exist_ok=True)
run(['javac','-J-Dfile.encoding=UTF-8','--release','8','-encoding','UTF-8','-classpath',os.pathsep.join(map(str,[android]+jars)),'-d',classes]+list((ROOT/'android/src').rglob('*.java')))
with zipfile.ZipFile(payload) as z:
    profile_manifest=json.loads(z.read('manifest.json'))
fixtures_ready=all((ROOT/'fixtures'/('original-'+p['id']+'.bundle')).is_file() for p in profile_manifest['profiles'])
if fixtures_ready:
    jsonjar=BUILD/'json.jar'
    if not jsonjar.exists():urllib.request.urlretrieve('https://repo.maven.apache.org/maven2/org/json/json/20250517/json-20250517.jar',jsonjar)
    tests=BUILD/'tests';tests.mkdir(exist_ok=True)
    run(['javac','-encoding','UTF-8','-classpath',os.pathsep.join(map(str,[classes,jsonjar,android])),'-d',tests,ROOT/'tests/EngineIntegrationTest.java'])
    run(['java','-Dfile.encoding=UTF-8','-Xmx1g','-classpath',os.pathsep.join(map(str,[tests,classes,jsonjar,android])),'dev.cesium.androidmin.EngineIntegrationTest',ROOT])
elif '--test' in sys.argv:
    raise RuntimeError('The exact original bundles listed in payload manifest are required for --test')
else:
    print('Fixture integration tests skipped: original game bundles are not included in the source archive.',flush=True)
jar=BUILD/'installer.jar';run(['jar','cf',jar,'-C',classes,'.'])
dex=BUILD/'dex';dex.mkdir(exist_ok=True)
run(['java','-cp',bt/'lib/d8.jar','com.android.tools.r8.D8','--min-api','26','--lib',android,'--output',dex,jar]+jars)
apk=BUILD/'unaligned.apk'
run([bt/'aapt2.exe','link','-I',android,'--manifest',ROOT/'android/AndroidManifest.xml','-A',ROOT/'android/assets','--min-sdk-version','26','--target-sdk-version','35','-o',apk])
with zipfile.ZipFile(apk,'a',zipfile.ZIP_DEFLATED) as z:z.write(dex/'classes.dex','classes.dex')
aligned=BUILD/'aligned.apk';run([bt/'zipalign.exe','-f','-p','4',apk,aligned])
default_output=ROOT/'outputs'
output_dir=Path(os.environ.get('CESIUM_OUTPUT_DIR',str(default_output)));output_dir.mkdir(parents=True,exist_ok=True)
output=output_dir/'CesiumLoader-Android-ModPack-0.4.0.apk'
key=Path(os.environ.get('CESIUM_KEYSTORE',str(TOOLS/'development-signing.p12')))
if not key.exists():
    key.parent.mkdir(parents=True,exist_ok=True)
    run(['keytool','-genkeypair','-keystore',key,'-storepass','android','-keypass','android','-alias','cesium-direct','-dname','CN=Cesium Direct Development','-keyalg','RSA','-keysize','2048','-validity','3650'])
run(['java','-jar',bt/'lib/apksigner.jar','sign','--ks',key,'--ks-pass','pass:android','--key-pass','pass:android','--out',output,aligned])
run(['java','-jar',bt/'lib/apksigner.jar','verify','--verbose',output])
print('APK',output,output.stat().st_size,hashlib.sha256(output.read_bytes()).hexdigest(),flush=True)
