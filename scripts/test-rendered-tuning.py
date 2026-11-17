#!/usr/bin/env python3
"""Measure F1/A4/C8 rendered by native sampler; requires ffmpeg (prep), numpy/scipy, C++ compiler."""
import argparse, importlib.util, json, math, pathlib, subprocess, tempfile
import numpy as np
root=pathlib.Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('prepare',root/'scripts/prepare-samples.py')
prep=importlib.util.module_from_spec(spec);spec.loader.exec_module(prep)
parser=argparse.ArgumentParser();parser.add_argument("--all",action="store_true");args=parser.parse_args()
manifest=json.loads((root/'app/src/main/assets/samples.json').read_text())
with tempfile.TemporaryDirectory() as tmp:
    tmp=pathlib.Path(tmp);binary=tmp/'render'
    subprocess.run(['cc','-O2','-c',str(root/'app/src/main/cpp/third_party/stb_vorbis.c'),'-o',str(tmp/'vorbis.o')],check=True)
    subprocess.run(['g++','-std=c++17','-O2',str(root/'tests/render_note.cpp'),str(tmp/'vorbis.o'),'-o',str(binary)],check=True)
    for midi in (range(29,109) if args.all else (29,69,108)):
        for layer in range(3):
            nearest=min(range(30,109,3),key=lambda r:abs(r-midi))
            s=next(s for s in manifest if s['midi']==nearest and s['layer']==layer)
            output=tmp/'render.f32'
            subprocess.run([str(binary),str(root/'app/src/main/assets'/s['path']),str(s['rootHz']),str(midi),str(layer),str(output)],check=True)
            data=np.fromfile(output,dtype='<f4').reshape(-1,2).mean(axis=1)
            ideal=440*2**((midi-69)/12)
            measured=prep.fundamental(data,48000,ideal)
            cents=1200*math.log2(measured/ideal)
            print(f'MIDI {midi} / layer {layer}: {measured:.4f} Hz, {cents:+.3f} cents')
            assert abs(cents)<3, (midi,layer,cents)
