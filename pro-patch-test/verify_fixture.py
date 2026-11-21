#!/usr/bin/env python3
"""Standalone CI checks for the MusicFlow Pro mock-entitlement experiment."""
import base64
import json
import pathlib
import re
import struct
import subprocess
import sys
import tempfile

root = pathlib.Path(__file__).resolve().parent
manifest = pathlib.Path(sys.argv[1])
wav_file = pathlib.Path(sys.argv[2])
code = (root / "app/src/main/java/com/musicflow/patchtest/MainActivity.java").read_text()
grade = (root / "app/build.gradle.kts").read_text()

assert 'applicationId = "com.musicflow.patchtest"' in grade, "MUST not replace production MusicFlow"
assert 'com.musicflow.preview' not in grade, "A separate package is required"
assert '"paid", false' in code and '"patch", false' in code, "Activation gates must default to OFF"
assert 'Signature.getInstance("SHA256withRSA")' in code, "Cryptographic verification required"
assert 'feature/musicflow-pro-demo-patch' in code, "Patch must originate from dedicated branch"
assert 'MediaStore.Audio.Media.RELATIVE_PATH' in code, "Use Android MediaStore"

payload = json.loads(manifest.read_text())
assert payload["payload"] == "musicflow-pro-test:v1:enable_download"
found = re.search(r'private static final String TEST_RSA_PUBLIC_KEY\s*=\s*"([^"]+)";', code)
assert found, "Missing pinned RSA public key in APK"
der = base64.b64decode(found.group(1), validate=True)
sig = base64.b64decode(payload["signature"], validate=True)
with tempfile.TemporaryDirectory() as tmp:
    folder = pathlib.Path(tmp)
    (folder / "pub.der").write_bytes(der)
    (folder / "sig.bin").write_bytes(sig)
    (folder / "payload.txt").write_bytes(payload["payload"].encode())
    subprocess.run(["openssl", "pkey", "-pubin", "-inform", "DER",
                    "-in", str(folder / "pub.der"), "-out", str(folder / "pub.pem")],
                   check=True, capture_output=True)
    verified = subprocess.run(["openssl", "dgst", "-sha256",
                               "-verify", str(folder / "pub.pem"),
                               "-signature", str(folder / "sig.bin"),
                               str(folder / "payload.txt")],
                              capture_output=True, text=True)
    assert verified.returncode == 0 and "Verified OK" in verified.stdout, "Signature invalid"

wav = base64.b64decode(wav_file.read_text().strip(), validate=True)
assert wav[:4] == b"RIFF" and wav[8:12] == b"WAVE"
assert wav[12:16] == b"fmt " and wav[36:40] == b"data"
assert struct.unpack_from("<I", wav, 40)[0] == len(wav) - 44
print("PASS: separate package, default-locked download, signed GitHub patch, original WAV fixture")
