#!/usr/bin/env python3
"""Offline standard-library check: full coverage, provenance hashes, roots and layers."""
import hashlib, json, math, pathlib
root=pathlib.Path(__file__).resolve().parents[1]/'app/src/main/assets'
entries=json.loads((root/'samples.json').read_text())
assert len(entries)==81
assert {(s['midi'],s['layer']) for s in entries} == {(m,l) for m in range(30,109,3) for l in range(3)}
for s in entries:
    assert hashlib.sha256((root/s['path']).read_bytes()).hexdigest()==s['sha256'],s['path']
    assert abs(s['correctionCents'])<150,s
    assert abs(1200*math.log2(s['nominalHz']/s['rootHz'])-s['correctionCents'])<.001
for midi in range(29,109):
    nearest=min(range(30,109,3),key=lambda m:abs(m-midi))
    assert abs(nearest-midi)<=1
assert sum((root/s['path']).stat().st_size for s in entries)<20_000_000
print('81 OGG verified: SHA256, 3 layers, full F1–C8 coverage, calibrated roots.')
