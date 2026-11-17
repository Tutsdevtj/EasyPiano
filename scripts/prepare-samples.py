#!/usr/bin/env python3
"""Reproduce bundled Salamander subset. Requires Python, numpy/scipy and ffmpeg.
Downloads immutable npm 1.0.5 packages, validates their registry SHA512,
retains full sample tails and calibrates each fundamental to equal temperament.
"""
import argparse, base64, hashlib, io, json, math, pathlib, subprocess, tarfile, urllib.request
import numpy as np
from scipy.signal import windows, find_peaks
ROOT=pathlib.Path(__file__).resolve().parents[1]
DEST=ROOT/'app/src/main/assets/samples'
LAYERS=(4,9,15)
def fundamental(data, rate, nominal):
    estimates=[]
    treble = nominal >= 1000
    duration = .35 if treble else 1.2
    for start in ((.02,.04,.06) if treble else (.25,.8,1.4)):
        segment=data[int(start*rate):int((start+duration)*rate)]
        if len(segment)<rate//4: continue
        segment=segment-segment.mean()
        nfft=2**math.ceil(math.log2(len(segment)*8))
        spectrum=np.abs(np.fft.rfft(segment*windows.blackmanharris(len(segment)),nfft))
        lo=int(nominal*2**(-1.5/12)*nfft/rate);hi=int(nominal*2**(1.5/12)*nfft/rate)+1
        peaks,_=find_peaks(spectrum[lo:hi])
        if not len(peaks): continue
        k=lo+peaks[np.argmax(spectrum[lo:hi][peaks])]
        a,b,c=np.log(spectrum[k-1:k+2]+1e-20)
        delta=.5*(a-c)/(a-2*b+c)
        estimates.append((k+delta)*rate/nfft)
    if not estimates: raise RuntimeError(f'No fundamental near {nominal}')
    return float(np.median(estimates))
def main():
    parser=argparse.ArgumentParser();parser.add_argument('--source',type=pathlib.Path,help='Directory with layer4/package/audio etc.');args=parser.parse_args()
    DEST.mkdir(parents=True,exist_ok=True)
    entries=[]
    for layer_index,layer in enumerate(LAYERS):
        if not args.source:
            url=f'https://registry.npmjs.org/@audio-samples/piano-velocity{layer}/1.0.5'
            meta=json.load(urllib.request.urlopen(url));blob=urllib.request.urlopen(meta['dist']['tarball']).read()
            expected=meta['dist']['integrity'].split('sha512-')[1]
            assert base64.b64encode(hashlib.sha512(blob).digest()).decode()==expected
            archive=tarfile.open(fileobj=io.BytesIO(blob),mode='r:gz')
        for midi in range(30,109,3):
            name=['C','C#','D','D#','E','F','F#','G','G#','A','A#','B'][midi%12]+str(midi//12-1)+f'v{layer}.ogg'
            source=(args.source/f'layer{layer}/package/audio'/name).read_bytes() if args.source else archive.extractfile('package/audio/'+name).read()
            # Retain original stereo OGG bytes. Avoid generational compression and long-note truncation.
            path=f'{midi}_{layer_index}.ogg';(DEST/path).write_bytes(source)
            pcm=subprocess.run(['ffmpeg','-v','error','-i','pipe:0','-f','f32le','-ac','1','-ar','24000','pipe:1'],input=source,stdout=subprocess.PIPE,check=True).stdout
            waveform=np.frombuffer(pcm,dtype='<f4')
            nominal=440*2**((midi-69)/12)
            measured=fundamental(waveform,24000,nominal)
            entries.append(dict(path='samples/'+path,midi=midi,layer=layer_index,rootHz=round(measured,8),nominalHz=round(nominal,8),correctionCents=round(1200*math.log2(nominal/measured),4),sha256=hashlib.sha256(source).hexdigest(),duration=round(len(waveform)/24000,3)))
            print(f'{name}: {measured:.4f} Hz -> {nominal:.4f} Hz',flush=True)
    (DEST.parent/'samples.json').write_text(json.dumps(entries,indent=2)+'\n')
    print(f'{len(entries)} samples, {sum((DEST/pathlib.Path(e["path"]).name).stat().st_size for e in entries)/1e6:.2f} MB')
if __name__=='__main__':main()
