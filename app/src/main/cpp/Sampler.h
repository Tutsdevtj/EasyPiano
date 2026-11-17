#pragma once
#include <array>
#include <atomic>
#include <algorithm>
#include <cmath>
#include <cstdint>
#include <vector>

struct Event { int type=0, id=0, note=0; float value=0; };
/** Single main-thread producer, single audio-thread consumer. Full queue requests a panic reset. */
class EventQueue {
    std::array<Event, 2048> data{};
    std::atomic<uint32_t> read{0}, write{0};
public:
    std::atomic<bool> overflow{false};
    bool push(Event e) {
        const auto w=write.load(std::memory_order_relaxed);
        const auto next=(w+1)%data.size();
        if (next==read.load(std::memory_order_acquire)) { overflow.store(true); return false; }
        data[w]=e; write.store(next,std::memory_order_release); return true;
    }
    bool pop(Event& e) {
        const auto r=read.load(std::memory_order_relaxed);
        if(r==write.load(std::memory_order_acquire)) return false;
        e=data[r]; read.store((r+1)%data.size(),std::memory_order_release); return true;
    }
    void clear() { Event e; while(pop(e)) {} overflow.store(false); }
};
struct Sample { std::vector<int16_t> pcm; int rate=0, frames=0; double rootHz=440; };
struct Voice {
    const Sample* sample=nullptr;
    double cursor=0, step=1;
    float gain=0, envelope=0, release=1;
    int id=0, note=0, age=0;
    bool held=false, releasing=false;
};
class Sampler {
public:
    // 27 minor-third sample roots, F#1 to C8, three dynamic layers.
    std::array<std::array<Sample,27>,3> samples;
    std::array<Voice,64> voices{};
    std::array<Voice,64> tails{};
    EventQueue queue;
    int sampleRate=48000, counter=0;
    bool sustain=false;
    float volume=.7f, targetVolume=.7f;
    void reset() { voices={}; tails={}; sustain=false; queue.clear(); }
    void release(Voice& v, float seconds=.24f) {
        v.releasing=true; v.release=std::exp(std::log(.0001f)/(seconds*sampleRate));
    }
    void apply(Event e) {
        if(e.type==0) {
            if(e.note<29 || e.note>108) return;
            const float velocity=std::clamp(e.value,.05f,1.f);
            const int layer=velocity<.38f?0:velocity<.73f?1:2;
            const int root=std::clamp(static_cast<int>(std::lround((e.note-30)/3.0)),0,26);
            const auto& sample=samples[layer][root];
            if(sample.frames<2) return;
            auto* v=&voices[0];
            for(auto& candidate:voices) {
                if(!candidate.sample) { v=&candidate; break; }
                if(candidate.age<v->age) v=&candidate;
            }
            if(v->sample) {
                // Preserve the stolen voice for a 5ms crossfade, using a preallocated tail pool.
                auto* tail=&tails[0];
                for(auto& t:tails) { if(!t.sample) {tail=&t;break;} if(t.age<tail->age) tail=&t; }
                *tail=*v; release(*tail,.005f);
            }
            const double hz=440.0*std::pow(2.0,(e.note-69)/12.0);
            *v=Voice{&sample,0,hz/sample.rootHz*sample.rate/sampleRate,.22f*(.35f+.65f*velocity),0,1,e.id,e.note,++counter,true,false};
        } else if(e.type==1) {
            for(auto& v:voices) if(v.sample && v.id==e.id && v.held) { v.held=false; if(!sustain) release(v,.22f + (108-v.note)*.003f); }
        } else if(e.type==2) {
            sustain=e.value>.5f;
            if(!sustain) for(auto& v:voices) if(v.sample && !v.held) release(v,.25f);
        } else if(e.type==3) targetVolume=std::clamp(e.value,0.f,1.f);
        else if(e.type==4) {
            sustain=false;
            for(auto& v:voices) if(v.sample) { v.held=false; release(v,.01f); }
        }
    }
    void render(float* output, int frames) {
        Event e;
        // Bound event work per callback. Overflow discards queued events and fades all notes.
        if(queue.overflow.exchange(false)) { queue.clear(); apply({4}); }
        for(int n=0;n<512 && queue.pop(e);++n) apply(e);
        std::fill(output,output+frames*2,0.f);
        auto mix=[&](Voice& v) {
            if(!v.sample) return;
            const auto& s=*v.sample;
            for(int f=0;f<frames;++f) {
                const int idx=static_cast<int>(v.cursor);
                if(idx+1>=s.frames || (v.releasing && v.envelope<.0001f)) {v.sample=nullptr;break;}
                if(v.releasing) v.envelope*=v.release;
                else v.envelope=std::min(1.f,v.envelope+1.f/(.002f*sampleRate));
                const float fraction=static_cast<float>(v.cursor-idx);
                // Natural end fade as well as key-release envelope, no loops/truncated bass notes.
                const float endFade=std::min(1.f,static_cast<float>((s.frames-1-v.cursor)/(.012*sampleRate)));
                for(int c=0;c<2;++c) {
                    const auto a=s.pcm[idx*2+c], b=s.pcm[(idx+1)*2+c];
                    output[f*2+c]+=((a+(b-a)*fraction)/32768.f)*v.gain*v.envelope*endFade;
                }
                v.cursor+=v.step;
            }
        };
        for(auto& v:voices) mix(v);
        for(auto& t:tails) mix(t);
        for(int f=0;f<frames;++f) {
            volume+=(targetVolume-volume)*.001f;
            for(int c=0;c<2;++c) {
                const float x=output[f*2+c]*volume;
                // Smooth bounded safety limiter. Identity near zero; no hard clipping.
                output[f*2+c]=x/std::sqrt(1.f+x*x);
            }
        }
    }
};
