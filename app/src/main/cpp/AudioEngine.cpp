#include "Sampler.h"
#include <jni.h>
#include <android/asset_manager_jni.h>
#include <oboe/Oboe.h>
#include <memory>
#include <string>
#define STB_VORBIS_HEADER_ONLY
#include "third_party/stb_vorbis.c"

class Engine final : public oboe::AudioStreamDataCallback, public oboe::AudioStreamErrorCallback {
public:
    Sampler sampler;
    std::shared_ptr<oboe::AudioStream> stream;
    std::atomic<bool> disconnected{false};
    ~Engine() { stop(); }
    bool start() {
        stop(); sampler.reset(); disconnected.store(false);
        oboe::AudioStreamBuilder b;
        b.setDirection(oboe::Direction::Output)->setPerformanceMode(oboe::PerformanceMode::LowLatency)
            ->setSharingMode(oboe::SharingMode::Exclusive)->setFormat(oboe::AudioFormat::Float)
            ->setChannelCount(2)->setUsage(oboe::Usage::Game)->setContentType(oboe::ContentType::Music)
            ->setDataCallback(this)->setErrorCallback(this);
        auto result=b.openStream(stream);
        if(result!=oboe::Result::OK) { b.setSharingMode(oboe::SharingMode::Shared); result=b.openStream(stream); }
        if(result!=oboe::Result::OK || !stream) return false;
        sampler.sampleRate=stream->getSampleRate();
        stream->setBufferSizeInFrames(stream->getFramesPerBurst()*2);
        if(stream->requestStart()!=oboe::Result::OK) {stop();return false;}
        return true;
    }
    void stop() { if(stream) {stream->requestStop();stream->close();stream.reset();} sampler.reset(); }
    oboe::DataCallbackResult onAudioReady(oboe::AudioStream*,void* data,int32_t frames) override {
        sampler.render(static_cast<float*>(data),frames); return oboe::DataCallbackResult::Continue;
    }
    void onErrorAfterClose(oboe::AudioStream*,oboe::Result) override { disconnected.store(true); }
};
static Engine* engine(jlong h) {return reinterpret_cast<Engine*>(h);}
extern "C" JNIEXPORT jlong JNICALL Java_com_arthur_easypiano_NativeAudio_create(JNIEnv*,jobject) {return reinterpret_cast<jlong>(new Engine());}
extern "C" JNIEXPORT jboolean JNICALL Java_com_arthur_easypiano_NativeAudio_loadSample(JNIEnv* env,jobject,jlong h,jobject assets,jstring path,jint midi,jint layer,jdouble rootHz) {
    if(midi<30 || midi>108 || (midi-30)%3 || layer<0 || layer>2) return false;
    const char* name=env->GetStringUTFChars(path,nullptr);
    auto* asset=AAssetManager_open(AAssetManager_fromJava(env,assets),name,AASSET_MODE_BUFFER);
    env->ReleaseStringUTFChars(path,name);
    if(!asset) return false;
    const auto length=AAsset_getLength(asset);
    std::vector<unsigned char> encoded(length);
    const auto read=AAsset_read(asset,encoded.data(),length); AAsset_close(asset);
    if(read!=length) return false;
    int channels=0,rate=0; short* decoded=nullptr;
    const int frames=stb_vorbis_decode_memory(encoded.data(),static_cast<int>(length),&channels,&rate,&decoded);
    if(frames<=0 || channels<1 || channels>2 || !decoded) {free(decoded);return false;}
    auto& s=engine(h)->sampler.samples[layer][(midi-30)/3];
    s.rate=rate; s.frames=frames; s.rootHz=rootHz;
    s.pcm.resize(static_cast<size_t>(frames)*2);
    for(int f=0;f<frames;++f) for(int c=0;c<2;++c) s.pcm[f*2+c]=decoded[f*channels+std::min(c,channels-1)];
    free(decoded); return true;
}
extern "C" JNIEXPORT jboolean JNICALL Java_com_arthur_easypiano_NativeAudio_start(JNIEnv*,jobject,jlong h) {return engine(h)->start();}
extern "C" JNIEXPORT void JNICALL Java_com_arthur_easypiano_NativeAudio_stop(JNIEnv*,jobject,jlong h) {engine(h)->stop();}
extern "C" JNIEXPORT void JNICALL Java_com_arthur_easypiano_NativeAudio_destroy(JNIEnv*,jobject,jlong h) {delete engine(h);}
extern "C" JNIEXPORT void JNICALL Java_com_arthur_easypiano_NativeAudio_event(JNIEnv*,jobject,jlong h,jint type,jint id,jint note,jfloat value) { engine(h)->sampler.queue.push({type,id,note,value}); }
extern "C" JNIEXPORT jstring JNICALL Java_com_arthur_easypiano_NativeAudio_status(JNIEnv* env,jobject,jlong h) {
    auto* e=engine(h); std::string text;
    if(e->disconnected.load()) text="disconnected";
    else if(!e->stream) text="stopped";
    else text=std::to_string(e->stream->getSampleRate())+" Hz · "+(e->stream->getSharingMode()==oboe::SharingMode::Exclusive?"Exclusive":"Shared")+" · "+std::to_string(e->stream->getBufferSizeInFrames())+" frames";
    return env->NewStringUTF(text.c_str());
}
