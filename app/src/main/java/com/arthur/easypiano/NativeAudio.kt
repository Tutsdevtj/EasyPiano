package com.arthur.easypiano
import android.content.res.AssetManager
/** All event producers run on the Android main thread (SPSC). Loading/closing is serialized by the Activity. */
class NativeAudio {
    companion object { init { System.loadLibrary("easypiano") } }
    external fun create(): Long
    external fun loadSample(handle: Long, assets: AssetManager, path: String, midi: Int, layer: Int, rootHz: Double): Boolean
    external fun start(handle: Long): Boolean
    external fun stop(handle: Long)
    external fun destroy(handle: Long)
    external fun event(handle: Long, type: Int, id: Int, note: Int, value: Float)
    external fun status(handle: Long): String
}
