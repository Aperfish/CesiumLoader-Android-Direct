"""Add reference runtimes for on-device experimental channel cache rebuilding.

Requires the locally supplied, exact original game bundles. They remain ignored by git.
The input payload's existing SDK, API and all 30 Mod files are retained byte for byte.
"""
from pathlib import Path
import hashlib, json, os, struct, sys, zipfile, zlib

ROOT = Path(__file__).resolve().parent
sys.path.insert(0, os.environ.get('CESIUM_PYTHON_LIBS', str(ROOT/'python-libs')))
import UnityPy

def sha(data):
    return hashlib.sha256(data).hexdigest()

def runtime(blob):
    found = []
    for obj in UnityPy.load(blob).objects:
        if obj.type.name == 'TextAsset':
            asset = obj.read()
            if asset.m_Name == 'AstralParty.Runtime.dll':
                found.append(asset.m_Script.encode('utf-8', 'surrogateescape') if isinstance(asset.m_Script, str) else bytes(asset.m_Script))
    assert len(found) == 1, 'expected one runtime TextAsset'
    return found[0]

def apply(original, patch):
    magic, old_size, new_size, controls, diffs, extras = struct.unpack('<8sQQIII', patch[:36])
    assert magic == b'CSDIFF01' and old_size == len(original)
    stream = zlib.decompress(patch[36:])
    assert len(stream) == controls+diffs+extras
    result = bytearray()
    old_pos, diff_pos, extra_pos = 0, controls, controls+diffs
    def offset(raw):
        value = int.from_bytes(raw, 'little')
        return -(value & 0x7fffffffffffffff) if value >> 63 else value
    for pos in range(0, controls, 24):
        x, y, z = (offset(stream[pos+i:pos+i+8]) for i in (0, 8, 16))
        assert x >= 0 and y >= 0
        result.extend((stream[diff_pos+i]+(original[old_pos+i] if 0 <= old_pos+i < len(original) else 0)) & 255 for i in range(x))
        result.extend(stream[extra_pos:extra_pos+y])
        old_pos += x+z
        diff_pos += x
        extra_pos += y
    assert len(result) == new_size and diff_pos == controls+diffs and extra_pos == len(stream)
    return bytes(result)

payload = ROOT/'android/assets/payload.zip'
with zipfile.ZipFile(payload) as archive:
    files = {n: archive.read(n) for n in archive.namelist() if not n.startswith('compatibility/')}
manifest = json.loads(files['manifest.json'])
for profile in manifest['profiles']:
    original = (ROOT/'fixtures'/('original-'+profile['id']+'.bundle')).read_bytes()
    assert sha(original) == profile['originalSha256']
    patched = apply(original, files['profiles/'+profile['id']+'/cache.csdiff'])
    assert sha(patched) == profile['patchedSha256']
    before, after = runtime(original), runtime(patched)
    name = 'compatibility/'+profile['id']+'/runtime.patched.dll'
    files[name] = after
    profile['channelRuntime'] = {'file': name, 'sha256': sha(after), 'originalSha256': sha(before), 'originalBytes': len(before)}
    print('CHANNEL REFERENCE', profile['id'], len(before), len(after), flush=True)
manifest['version'] = '0.5.0'
manifest['channelCompatibility'] = 1
files['manifest.json'] = json.dumps(manifest, ensure_ascii=False, indent=2).encode('utf-8')
temp = payload.with_suffix('.channel.zip')
with zipfile.ZipFile(temp, 'w', zipfile.ZIP_DEFLATED) as archive:
    for name, data in files.items():
        archive.writestr(name, data)
temp.replace(payload)
print('PAYLOAD', payload.stat().st_size, sha(payload.read_bytes()))
