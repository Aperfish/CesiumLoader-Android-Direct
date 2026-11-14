"""Verify an immutable pre-signed APK and upload/publish it from GitHub Actions.

This is a publication job, not a claim that CI rebuilt proprietary game inputs.
The signing key stays on the build machine. APK parts live in a separate input
branch; the default branch contains source, release notes and digest metadata.
"""
from pathlib import Path
import argparse, concurrent.futures, hashlib, io, json, os, re, time
import urllib.error, urllib.parse, urllib.request, zipfile

REPOSITORY = 'Aperfish/CesiumLoader-Android-Direct'
ROOT = Path(__file__).resolve().parents[1]
API = 'https://api.github.com/repos/' + REPOSITORY


def request(url, *, method='GET', data=None, content_type='application/json', raw=False):
    if not (url.startswith(API + '/') or url.startswith('https://uploads.github.com/repos/' + REPOSITORY + '/')):
        raise ValueError('Unexpected publication destination')
    headers = {'Accept': 'application/vnd.github.raw+json' if raw else 'application/vnd.github+json',
               'User-Agent': 'CesiumLoader-Android-Direct-release',
               'Authorization': 'Bearer ' + os.environ['GITHUB_TOKEN'],
               'X-GitHub-Api-Version': '2022-11-28'}
    if data is not None:
        headers['Content-Type'] = content_type
        if not isinstance(data, bytes):
            data = json.dumps(data).encode('utf-8')
    for attempt in range(4):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, data=data, headers=headers, method=method), timeout=180) as response:
                value = response.read()
                return value if raw else json.loads(value) if value else None
        except urllib.error.HTTPError as error:
            if method != 'GET' or error.code not in (429, 500, 502, 503, 504) or attempt == 3:
                raise
            time.sleep(2 ** attempt)


def validate_apk(apk, manifest):
    if len(apk) != manifest['bytes'] or hashlib.sha256(apk).hexdigest() != manifest['sha256']:
        raise RuntimeError('APK size/SHA-256 mismatch')
    with zipfile.ZipFile(io.BytesIO(apk)) as archive:
        with zipfile.ZipFile(io.BytesIO(archive.read('assets/payload.zip'))) as payload:
            metadata = json.loads(payload.read('manifest.json'))
            if metadata['version'] != manifest['version'] or len(metadata['mods']) != 30:
                raise RuntimeError('Unexpected payload version/Mod count')
            camera = next(mod for mod in metadata['mods'] if mod['id'] == 'FreeCameraMod')
            if camera['name'] != '自由相机（双指缩放）':
                raise RuntimeError('APK does not contain zoom-only camera controls')


def download_part(part):
    blob = request(API + '/git/blobs/' + part['gitBlob'], raw=True)
    git_sha = hashlib.sha1(('blob ' + str(len(blob)) + '\0').encode() + blob).hexdigest()
    if len(blob) != part['bytes'] or git_sha != part['gitBlob'] or hashlib.sha256(blob).hexdigest() != part['sha256']:
        raise RuntimeError('APK part digest mismatch: ' + str(part['index']))
    return blob


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--manifest', type=Path, required=True)
    parser.add_argument('--verify-apk', type=Path, help='Verify a local APK without GitHub writes')
    args = parser.parse_args()
    manifest = json.loads(args.manifest.read_text(encoding='utf-8'))
    version, tag = manifest['version'], manifest['tag']
    if not re.fullmatch(r'\d+\.\d+\.\d+', version) or tag != 'v' + version or manifest['apk'] != 'CesiumLoader-Android-ModPack-' + version + '.apk':
        raise RuntimeError('Unexpected release identity')
    if args.verify_apk:
        validate_apk(args.verify_apk.read_bytes(), manifest)
        print('Verified exact signed APK:', manifest['apk'], manifest['sha256'])
        return
    if os.environ.get('GITHUB_REPOSITORY') != REPOSITORY or not os.environ.get('GITHUB_SHA'):
        raise RuntimeError('Publish only from the authorized GitHub Actions repository')
    print('Downloading immutable signed APK parts:', len(manifest['chunks']), flush=True)
    with concurrent.futures.ThreadPoolExecutor(max_workers=6) as workers:
        apk = b''.join(workers.map(download_part, manifest['chunks']))
    validate_apk(apk, manifest)
    print('Verified APK SHA-256:', manifest['sha256'], flush=True)
    checksum = (manifest['sha256'] + '  ' + manifest['apk'] + '\n').encode('ascii')
    notes = (ROOT/'releases'/(tag + '.md')).read_text(encoding='utf-8')
    source = os.environ['GITHUB_SHA']
    try:
        release = request(API + '/releases/tags/' + tag)
    except urllib.error.HTTPError as error:
        if error.code != 404:
            raise
        release = request(API + '/releases', method='POST', data={
            'tag_name': tag, 'target_commitish': source,
            'name': tag + ' 测试版：渠道实验兼容与双指缩放',
            'body': notes, 'draft': True, 'prerelease': True, 'make_latest': 'false'})
    if release['target_commitish'] != source:
        raise RuntimeError('Existing release points to a different source commit')
    if not release['draft'] and not release['prerelease']:
        raise RuntimeError('Refusing to change an existing stable release')
    for name, data, mime in ((manifest['apk'], apk, 'application/vnd.android.package-archive'),
                             ('SHA256SUMS.txt', checksum, 'text/plain')):
        digest = 'sha256:' + hashlib.sha256(data).hexdigest()
        assets = request(API + '/releases/' + str(release['id']) + '/assets')
        existing = next((asset for asset in assets if asset['name'] == name), None)
        if existing:
            if existing['size'] != len(data) or existing.get('digest') != digest:
                raise RuntimeError('Existing asset differs; no overwrite: ' + name)
            print('Asset already verified:', name, flush=True)
            continue
        uploaded = request('https://uploads.github.com/repos/' + REPOSITORY + '/releases/' + str(release['id']) + '/assets?name=' + urllib.parse.quote(name),
                           method='POST', data=data, content_type=mime)
        if uploaded['state'] != 'uploaded' or uploaded['size'] != len(data) or uploaded.get('digest') != digest:
            raise RuntimeError('Uploaded asset verification failed: ' + name)
        print('Uploaded:', name, flush=True)
    published = request(API + '/releases/' + str(release['id']), method='PATCH',
                        data={'draft': False, 'prerelease': True, 'make_latest': 'false'})
    if published['draft'] or not published['prerelease']:
        raise RuntimeError('Pre-release publication not confirmed')
    print('Published pre-release:', published['html_url'], flush=True)
    summary = os.environ.get('GITHUB_STEP_SUMMARY')
    if summary:
        with open(summary, 'a', encoding='utf-8') as out:
            out.write('Published [' + tag + '](' + published['html_url'] + ') from `' + source + '`.\n\n')
            out.write('APK SHA-256: `' + manifest['sha256'] + '`\n\n30 Mods; zoom-only camera; experimental channel installation.\n')


if __name__ == '__main__':
    main()
