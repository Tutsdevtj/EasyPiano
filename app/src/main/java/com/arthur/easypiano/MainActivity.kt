package com.arthur.easypiano

import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray

class MainActivity : ComponentActivity() {
    private val native=NativeAudio()
    private var handle=0L
    private val sampleLock=Mutex()
    private var loaded=false
    private var ready by mutableStateOf(false)
    private var progress by mutableFloatStateOf(0f)
    private var error by mutableStateOf<String?>(null)
    private var status by mutableStateOf("")
    private var generation by mutableIntStateOf(0)
    private var settings by mutableStateOf(Settings())
    private var hasFocus=false
    private lateinit var manager: AudioManager
    private lateinit var focusRequest: AudioFocusRequest
    private var loading: Job?=null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if(Build.VERSION.SDK_INT>=28) window.attributes=window.attributes.apply {layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES}
        immerse()
        manager=getSystemService(AudioManager::class.java)
        volumeControlStream=AudioManager.STREAM_MUSIC
        focusRequest=AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setOnAudioFocusChangeListener({ focus ->
                if(focus==AudioManager.AUDIOFOCUS_GAIN) {hasFocus=true;if(loaded && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))startAudio()}
                else {hasFocus=false;pauseAudio()}
            },android.os.Handler(mainLooper)).build()
        handle=native.create()
        val store=SettingsStore(this)
        setContent {
            var restored by remember {mutableStateOf(false)}
            LaunchedEffect(Unit) {settings=store.settings.first();restored=true}
            LaunchedEffect(settings,restored) {if(restored) {delay(300);store.save(settings)}}
            LandscapeFrame {
            PianoScreen(settings,{settings=it},ready,progress,error,status,generation,
                {type,id,note,value -> if(ready) native.event(handle,type,id,note,value)},
                {if(loaded)acquireAudio() else loadSamples()})
            }
        }
        loadSamples()
        lifecycleScope.launch {
            while(isActive) {
                delay(700)
                if(loaded && ready) {
                    status=native.status(handle)
                    if(status=="disconnected" && hasFocus && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))startAudio()
                }
            }
        }
    }
    private fun loadSamples() {
        if(loading?.isActive==true)return
        error=null;progress=0f
        loading=lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    sampleLock.withLock {
                        val manifest=JSONArray(assets.open("samples.json").bufferedReader().use{it.readText()})
                        for(i in 0 until manifest.length()) {
                            ensureActive()
                            val s=manifest.getJSONObject(i)
                            check(native.loadSample(handle,assets,s.getString("path"),s.getInt("midi"),s.getInt("layer"),s.getDouble("rootHz"))) {"Sample inválido: ${s.getString("path")}"}
                            withContext(Dispatchers.Main) {progress=(i+1f)/manifest.length()}
                        }
                    }
                }
                loaded=true
                if(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))acquireAudio()
            } catch(e:CancellationException) {throw e}
            catch(e:Exception) {error="Não foi possível carregar o piano: ${e.message}"}
        }
    }
    private fun acquireAudio() {
        if(!loaded)return
        hasFocus=manager.requestAudioFocus(focusRequest)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if(hasFocus)startAudio() else {ready=false;error="O áudio está sendo usado por outro aplicativo."}
    }
    private fun startAudio() {
        ready=false;generation++
        ready=native.start(handle)
        if(ready) {native.event(handle,3,0,0,settings.volume);status=native.status(handle);error=null}
        else error="Não foi possível abrir a saída de áudio. Toque em tentar novamente."
    }
    private fun pauseAudio() {
        ready=false;generation++
        if(loaded)native.stop(handle)
    }
    override fun onResume() {super.onResume();immerse();if(loaded)acquireAudio()}
    override fun onPause() {
        pauseAudio()
        if(::manager.isInitialized)manager.abandonAudioFocusRequest(focusRequest)
        hasFocus=false
        // Persist immediately as well as debouncing edits, even when leaving during a drag.
        lifecycleScope.launch {SettingsStore(this@MainActivity).save(settings)}
        super.onPause()
    }
    override fun onWindowFocusChanged(hasFocus:Boolean) {super.onWindowFocusChanged(hasFocus);if(hasFocus)immerse()}
    private fun immerse() {
        WindowCompat.getInsetsController(window,window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior=WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
    override fun onDestroy() {
        ready=false;loading?.cancel()
        val oldHandle=handle
        CoroutineScope(Dispatchers.IO).launch {sampleLock.withLock {native.destroy(oldHandle)}}
        super.onDestroy()
    }
}
