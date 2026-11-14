"""Recover the exact offline payload from a verified published APK; no game binaries enter Git."""
from pathlib import Path
import argparse, hashlib, io, json, zipfile

root = Path(__file__).resolve().parent
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--apk', type=Path, required=True)
parser.add_argument('--sha256', default='80edd88c27f3f3fc5ed7c65e98acca13ab0bddf58d9042767af3e2b2fe07f3c2',
                    help='Expected APK SHA-256; defaults to the v0.4.0 published artifact')
args = parser.parse_args()
apk = args.apk.read_bytes()
if hashlib.sha256(apk).hexdigest() != args.sha256.lower():
    raise SystemExit('APK SHA-256 mismatch: use the published artifact or explicitly provide its verified digest.')
with zipfile.ZipFile(io.BytesIO(apk)) as archive:
    payload = archive.read('assets/payload.zip')
    mod_list = archive.read('assets/mod-list.json')
with zipfile.ZipFile(io.BytesIO(payload)) as archive:
    manifest = json.loads(archive.read('manifest.json'))
    if manifest.get('format') != 2 or manifest.get('version') != '0.4.0':
        raise SystemExit('Unsupported payload format/version')
    for name, digest in manifest['commonFiles'].items():
        if hashlib.sha256(archive.read('common/' + name)).hexdigest() != digest:
            raise SystemExit('Common payload hash mismatch: ' + name)
    for profile in manifest['profiles']:
        prefix = 'profiles/' + profile['id'] + '/'
        if hashlib.sha256(archive.read(prefix + 'cache.csdiff')).hexdigest() != profile['deltaSha256']:
            raise SystemExit('Profile delta mismatch: ' + profile['id'])
        for name, digest in profile['files'].items():
            if hashlib.sha256(archive.read(prefix + name)).hexdigest() != digest:
                raise SystemExit('Profile file mismatch: ' + name)
if [m['id'] for m in json.loads(mod_list)] != [m['id'] for m in manifest['mods']]:
    raise SystemExit('Mod list and payload disagree')
assets = root / 'android/assets'
assets.mkdir(parents=True, exist_ok=True)
(assets / 'payload.zip').write_bytes(payload)
(assets / 'mod-list.json').write_bytes(mod_list)
print('Prepared verified', manifest['version'], 'payload:', len(manifest['mods']), 'mods,', len(manifest['profiles']), 'profiles')
