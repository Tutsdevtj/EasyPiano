// Offline harness using the same sampler and Vorbis decoder as Android.
#include "../app/src/main/cpp/Sampler.h"
#include <fstream>
#include <iterator>
#include <iostream>
#define STB_VORBIS_HEADER_ONLY
#include "../app/src/main/cpp/third_party/stb_vorbis.c"
int main(int argc,char** argv) {
    if(argc!=6)return 1;
    std::ifstream input(argv[1],std::ios::binary);
    std::vector<unsigned char> bytes((std::istreambuf_iterator<char>(input)),{});
    int channels,rate;short* pcm=nullptr;
    int frames=stb_vorbis_decode_memory(bytes.data(),bytes.size(),&channels,&rate,&pcm);
    if(frames<=0)return 2;
    const int midi=std::stoi(argv[3]),layer=std::stoi(argv[4]);
    Sampler s;auto& sample=s.samples[layer][std::clamp(static_cast<int>(std::lround((midi-30)/3.0)),0,26)];
    sample.rootHz=std::stod(argv[2]);sample.rate=rate;sample.frames=frames;sample.pcm.resize(frames*2);
    for(int f=0;f<frames;++f)for(int c=0;c<2;++c)sample.pcm[f*2+c]=pcm[f*channels+std::min(c,channels-1)];
    free(pcm);
    const float velocities[]={.3f,.6f,1.f};s.queue.push({0,1,midi,velocities[layer]});
    std::ofstream out(argv[5],std::ios::binary);std::array<float,512> block{};
    for(int i=0;i<600;++i){s.render(block.data(),256);out.write(reinterpret_cast<char*>(block.data()),sizeof(block));}
}
