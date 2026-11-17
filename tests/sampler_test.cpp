#include "../app/src/main/cpp/Sampler.h"
#include <cassert>
#include <iostream>
int main() {
    Sampler s;
    for(auto& layer:s.samples) for(auto& sample:layer) {sample.rate=48000;sample.frames=48000;sample.pcm.assign(96000,3276);sample.rootHz=440;}
    std::array<float,256*2> out{};
    s.queue.push({0,1,69,1});s.render(out.data(),256);
    assert(s.voices[0].held); assert(s.voices[0].step==1);
    s.queue.push({2,0,0,1});s.queue.push({1,1});s.render(out.data(),256);
    assert(!s.voices[0].held && !s.voices[0].releasing);
    s.queue.push({2});s.render(out.data(),256);assert(s.voices[0].releasing);
    for(int n=0;n<200;++n) s.render(out.data(),256);
    assert(!s.voices[0].sample);
    for(int n=0;n<70;++n) s.queue.push({0,n,69,1});
    s.render(out.data(),256);
    assert(std::count_if(s.voices.begin(),s.voices.end(),[](auto v){return v.sample!=nullptr;})==64);
    assert(s.voices[0].age>1);
    for(int n=0;n<3000;++n)s.queue.push({0,n,69,1});
    s.render(out.data(),256);for(int n=0;n<30;++n)s.render(out.data(),256);
    for(auto v:s.voices) assert(!v.sample);
    for(float x:out)assert(std::isfinite(x));
    std::cout<<"Sampler: tuning ratio, sustain, release, 64 voices, stealing and queue overflow OK\n";
}
