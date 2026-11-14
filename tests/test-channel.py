"""Generate channel packaging variants and verify all non-runtime assets survive installation."""
from pathlib import Path
import hashlib, os, subprocess, sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, os.environ.get('CESIUM_PYTHON_LIBS', str(ROOT/'python-libs')))
import UnityPy

def assets(blob):
    result = {}
    for obj in UnityPy.load(blob).objects:
        if obj.type.name == 'TextAsset':
            asset = obj.read()
            result[asset.m_Name] = asset.m_Script.encode('utf-8', 'surrogateescape') if isinstance(asset.m_Script, str) else bytes(asset.m_Script)
    return result

out = ROOT/'build/channel-fixtures'
out.mkdir(parents=True, exist_ok=True)
source = next(iter(sorted((ROOT/'fixtures').glob('original-*.bundle'))))
env = UnityPy.load(str(source))
(out/'repacked.bundle').write_bytes(env.file.save(packer='none'))
env = UnityPy.load(str(sorted((ROOT/'fixtures').glob('original-*.bundle'))[-1]))
tagged = False
for obj in env.objects:
    if obj.type.name == 'TextAsset':
        a = obj.read()
        if a.m_Name == 'AstralParty.Runtime.dll':
            a.m_Script += '\nCHANNEL-COMPATIBILITY-TEST\n'
            a.save()
        elif not tagged:
            a.m_Script += '\nKEEP-CHANNEL-SPECIFIC-CONTENT\n'
            a.save()
            tagged = True
(out/'different-runtime.bundle').write_bytes(env.file.save(packer='lz4'))

tools = Path(os.environ.get('CESIUM_BUILD_TOOLS', str(ROOT/'tools')))
classes = ROOT/'build/java'
tests = ROOT/'build/tests'
cp = os.pathsep.join(map(str, [tests, classes, ROOT/'build/json.jar', tools/'platform35/android-35/android.jar']))
subprocess.run(['javac', '-encoding', 'UTF-8', '-classpath', cp, '-d', str(tests), str(ROOT/'tests/ChannelCompatibilityTest.java')], check=True)
subprocess.run(['java', '-Dfile.encoding=UTF-8', '-Xmx256m', '-classpath', cp, 'dev.cesium.androidmin.ChannelCompatibilityTest', str(ROOT)], check=True)
for kind in ('repacked', 'different-runtime'):
    before = assets((out/(kind+'.bundle')).read_bytes())
    after = assets((out/(kind+'.patched.bundle')).read_bytes())
    assert before.keys() == after.keys(), kind
    for name in before:
        if name != 'AstralParty.Runtime.dll':
            assert before[name] == after[name], (kind, name)
        else:
            assert before[name] != after[name], kind
    print('PASS independently parsed UnityFS;', kind, '; all', len(before)-1, 'non-runtime TextAssets unchanged')
