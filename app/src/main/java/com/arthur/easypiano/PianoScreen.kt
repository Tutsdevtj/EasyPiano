package com.arthur.easypiano

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import java.util.Locale

private val Gold = Color(0xFFDDC08C)
private val Panel = Color(0xFF151A1B)
/** Android 17 may ignore orientation requests on large displays. Keep the instrument landscape inside that window as well. */
@Composable
fun LandscapeFrame(content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF0B1011)).windowInsetsPadding(WindowInsets.displayCutout), contentAlignment=Alignment.Center) {
        if (maxWidth < maxHeight) {
            Box(Modifier.requiredSize(maxHeight, maxWidth).graphicsLayer(rotationZ=90f)) { content() }
        } else content()
    }
}
@Composable
fun PianoScreen(s: Settings, update: (Settings)->Unit, ready: Boolean, progress: Float, error: String?,
                audioStatus: String, generation: Int, event: (Int,Int,Int,Float)->Unit, retry: ()->Unit) {
    var pressed by remember { mutableStateOf<Map<Long,Int>>(emptyMap()) }
    var lastNote by remember { mutableStateOf<PianoNote?>(null) }
    var sustain by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf(false) }
    var about by remember { mutableStateOf(false) }
    LaunchedEffect(generation, ready) { pressed=emptyMap();sustain=false }
    LaunchedEffect(dialog) { if(dialog) {event(4,0,0,0f);pressed=emptyMap();sustain=false} }
    fun position(p:Float) { update(s.copy(position=Piano.clamp(p,s.zoom))) }
    MaterialTheme(colorScheme=darkColorScheme(primary=Gold, background=Panel, surface=Panel)) {
        Column(Modifier.fillMaxSize().background(Color(0xFF0B1011)).padding(horizontal=8.dp)) {
            Row(Modifier.fillMaxWidth().height(42.dp), verticalAlignment=Alignment.CenterVertically) {
                Text("EASY", color=Color(0xFFF4EEE1), fontSize=18.sp, fontWeight=FontWeight.Light, letterSpacing=3.sp)
                Text("PIANO", color=Gold, fontSize=18.sp, fontWeight=FontWeight.Bold, letterSpacing=3.sp)
                Spacer(Modifier.width(20.dp))
                val note=lastNote
                Text(if(note==null) "F1 — C8  ·  GRAND PIANO" else "${note.label(s.notation,s.accidentals)} · ${String.format(Locale.forLanguageTag("pt-BR"),"%.1f",note.frequency)} Hz",
                    color=Gold, fontSize=14.sp, modifier=Modifier.weight(1f), maxLines=1)
                Text("VOL",color=Color.Gray,fontSize=10.sp)
                Slider(s.volume,{update(s.copy(volume=it));event(3,0,0,it)},Modifier.width(110.dp).height(36.dp))
                TextButton(onClick={dialog=true},contentPadding=PaddingValues(horizontal=10.dp)) {Text("Ajustes",fontSize=12.sp)}
            }
            MiniMap(s,::position,{update(s.copy(position=Piano.snap(it,s.zoom,s.snap)))})
            Row(Modifier.fillMaxWidth().height(17.dp),horizontalArrangement=Arrangement.SpaceBetween) {
                Text("F1",color=Color.Gray,fontSize=9.sp)
                Text("SALAMANDER · YAMAHA C5",color=Color(0xFF77817E),fontSize=9.sp,letterSpacing=2.sp)
                Text("C8",color=Color.Gray,fontSize=9.sp)
            }
            Box(Modifier.fillMaxWidth().weight(1f)) {
                Keyboard(s,pressed.values.toSet(),ready && !dialog,generation,{id,note,velocity->
                    if(note!=null) {event(0,id.toInt(),note.midi,velocity);pressed=pressed+(id to note.midi);lastNote=note}
                    else {event(1,id.toInt(),0,0f);pressed=pressed-id}
                })
                if(!ready) Box(Modifier.fillMaxSize().background(Color(0xED111718)),contentAlignment=Alignment.Center) {
                    Column(horizontalAlignment=Alignment.CenterHorizontally,modifier=Modifier.padding(24.dp)) {
                        Text(error ?: "Preparando seu piano… ${(progress*100).toInt()}%",color=Gold)
                        if(error==null) {Spacer(Modifier.height(16.dp));LinearProgressIndicator(progress={progress},modifier=Modifier.width(260.dp))}
                        else Button(onClick=retry) {Text("Tentar novamente")}
                    }
                }
            }
            Row(Modifier.fillMaxWidth().height(56.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                NavButton("◀",s.locked) {position(s.position-7f)}
                NavButton("‹",s.locked) {position(s.position-1f)}
                NavButton("›",s.locked) {position(s.position+1f)}
                NavButton("▶",s.locked) {position(s.position+7f)}
                TextButton(onClick={update(s.copy(locked=!s.locked))},contentPadding=PaddingValues(4.dp)) {Text(if(s.locked) "Travado" else "Travar",fontSize=11.sp)}
                Text("ZOOM",color=Color.Gray,fontSize=10.sp)
                Slider(s.zoom,{z->update(s.copy(zoom=z,position=Piano.clamp(s.position,z)))},
                    Modifier.weight(1f).height(40.dp),enabled=!s.locked,valueRange=7f..47f,steps=39,
                    onValueChangeFinished={position(Piano.snap(s.position,s.zoom,s.snap))})
                Text("${s.zoom.toInt()}",color=Gold,fontSize=12.sp)
                Box(Modifier.padding(start=8.dp).width(130.dp).height(40.dp).background(if(sustain) Gold else Color(0xFF283134),shape=MaterialTheme.shapes.small)
                    .semantics {contentDescription="Pedal de sustain"}
                    .pointerInput(s.pedalToggle,ready,generation) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed=false)
                            if(ready) {
                                if(s.pedalToggle) {sustain=!sustain;event(2,0,0,if(sustain) 1f else 0f)}
                                else {sustain=true;event(2,0,0,1f)}
                            }
                            try { do {val e=awaitPointerEvent();e.changes.forEach {it.consume()}} while(e.changes.any {it.pressed}) }
                            finally {if(!s.pedalToggle){sustain=false;event(2,0,0,0f)}}
                        }
                    },contentAlignment=Alignment.Center) {
                    Text(if(sustain) "SUSTAIN ●" else "SUSTAIN",color=if(sustain) Color.Black else Gold,fontSize=12.sp,fontWeight=FontWeight.Bold)
                }
            }
        }
        if(dialog) AlertDialog(onDismissRequest={dialog=false},title={Text(if(about) "Sobre o EasyPiano" else "Seu piano, do seu jeito")},
            text={Column(Modifier.heightIn(max=260.dp).verticalScroll(rememberScrollState())) {
                if(about) {
                    Text("Salamander Grand Piano V3 — Alexander Holm. Gravações reais de um Yamaha C5, licenciadas em CC BY 3.0. Distribuição OGG: darosh/samples-piano.\n\nAdaptação: três camadas (v4, v9, v15), amostras a cada três semitons e correção de afinação por sample.\n\ncreativecommons.org/licenses/by/3.0/\nsfzinstruments.github.io/pianos/salamander/\n\nÁudio: Oboe (Apache 2.0), stb_vorbis (MIT/domínio público).\n64 vozes + caudas de fade. A4 = 440 Hz.\n\n$audioStatus\n\nFones com fio oferecem menor atraso. Bluetooth adiciona a latência do próprio dispositivo.")
                } else {
                    Choice("Nomes",listOf("Nenhum","Só os Dós","Brancas","Todas"),s.labels.ordinal){update(s.copy(labels=Labels.entries[it]))}
                    Choice("Notação",listOf("C D E","Dó Ré Mi"),s.notation.ordinal){update(s.copy(notation=Notation.entries[it]))}
                    Choice("Pretas",listOf("C#","Db","C# / Db"),s.accidentals.ordinal){update(s.copy(accidentals=Accidentals.entries[it]))}
                    Choice("Encaixar em",listOf("Livre","Dó","Fá"),s.snap.ordinal){update(s.copy(snap=Snap.entries[it]))}
                    SettingSwitch("Intensidade pela altura do toque",s.velocity){update(s.copy(velocity=it))}
                    SettingSwitch("Sustain por alternância",s.pedalToggle){sustain=false;event(2,0,0,0f);update(s.copy(pedalToggle=it))}
                    TextButton(onClick={about=true}) {Text("Sobre · samples e licenças")}
                }
            }},confirmButton={TextButton(onClick={if(about)about=false else dialog=false}){Text(if(about) "Voltar" else "Pronto")}})
    }
}
@Composable private fun NavButton(text:String,locked:Boolean,action:()->Unit) {
    TextButton(onClick=action,enabled=!locked,modifier=Modifier.width(38.dp),contentPadding=PaddingValues(0.dp)) {Text(text,fontSize=23.sp)}
}
@Composable private fun Choice(title:String,options:List<String>,selected:Int,onSelect:(Int)->Unit) {
    Text(title,color=Gold,fontSize=12.sp)
    Row(horizontalArrangement=Arrangement.spacedBy(5.dp)) { options.forEachIndexed {i,t->FilterChip(selected=i==selected,onClick={onSelect(i)},label={Text(t,fontSize=11.sp)})} }
}
@Composable private fun SettingSwitch(title:String,value:Boolean,change:(Boolean)->Unit) {
    Row(verticalAlignment=Alignment.CenterVertically) {Text(title,Modifier.weight(1f),fontSize=12.sp);Switch(value,change)}
}

@Composable private fun MiniMap(s:Settings,move:(Float)->Unit,settle:(Float)->Unit) {
    val current by rememberUpdatedState(s)
    val moveNow by rememberUpdatedState(move)
    val settleNow by rememberUpdatedState(settle)
    val scope=rememberCoroutineScope()
    var fling by remember {mutableStateOf<Job?>(null)}
    DisposableEffect(s.locked) { onDispose {fling?.cancel()} }
    Canvas(Modifier.fillMaxWidth().height(32.dp).semantics {contentDescription="Minimapa F1 até C8. Arraste para posicionar o teclado."}
        .pointerInput(s.locked) {
            if(!s.locked) awaitEachGesture {
                val down=awaitFirstDown();fling?.cancel()
                val tracker=VelocityTracker();tracker.addPosition(down.uptimeMillis,down.position)
                val scale=47f/size.width
                val tapped=down.position.x*scale
                val anchor=if(tapped in current.position..(current.position+current.zoom)) tapped-current.position else current.zoom/2
                moveNow(tapped-anchor)
                var held=true
                while(held) {
                    val e=awaitPointerEvent();val p=e.changes.firstOrNull {it.id==down.id}
                    if(p==null || !p.pressed) held=false
                    else {tracker.addPosition(p.uptimeMillis,p.position);moveNow(p.position.x*scale-anchor)}
                    e.changes.forEach {it.consume()}
                }
                val velocity=tracker.calculateVelocity().x*scale
                fling=scope.launch {
                    val a=Animatable(current.position);a.updateBounds(0f,(47f-current.zoom).coerceAtLeast(0f))
                    if(abs(velocity)>.5f) a.animateDecay(velocity,exponentialDecay()) {moveNow(value)}
                    settleNow(current.position)
                }
            }
        }) {
        val w=size.width/47f
        drawRect(Color(0xFFE4E1D7))
        Piano.keys.filterNot{it.note.black}.forEach {k->drawLine(Color(0xFF434B4A),Offset(k.left*w,0f),Offset(k.left*w,size.height),.7f)}
        Piano.keys.filter{it.note.black}.forEach{k->drawRect(Color(0xFF171C1D),Offset(k.left*w,0f),Size(k.width*w,size.height*.64f))}
        drawRect(Gold.copy(alpha=.23f),Offset(s.position*w,0f),Size(s.zoom*w,size.height))
        drawRect(Gold,Offset(s.position*w,1f),Size(s.zoom*w,size.height-2),style=Stroke(2.dp.toPx()))
    }
}

@Composable private fun Keyboard(s:Settings,pressed:Set<Int>,enabled:Boolean,generation:Int,touch:(Long,PianoNote?,Float)->Unit) {
    val current by rememberUpdatedState(s)
    val touchNow by rememberUpdatedState(touch)
    val animation=Piano.notes.associate {it.midi to animateFloatAsState(if(it.midi in pressed)1f else 0f,tween(75),label="key${it.midi}")}
    val paint=remember {Paint(Paint.ANTI_ALIAS_FLAG).apply {textAlign=Paint.Align.CENTER;typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL)}}
    val density=LocalDensity.current.density
    Canvas(Modifier.fillMaxSize().semantics {contentDescription="Teclado de piano multitouch"}.pointerInput(enabled,generation) {
        if(!enabled) return@pointerInput
        val held=mutableMapOf<Long,PianoNote>()
        try {
            awaitPointerEventScope {
                while(true) {
                    val e=awaitPointerEvent(PointerEventPass.Main)
                    for(p in e.changes) {
                        val id=p.id.value
                        val note=if(p.pressed && p.position.x >= 0 && p.position.x < size.width && p.position.y >= 0 && p.position.y < size.height) Piano.hit(current.position+p.position.x/size.width*current.zoom,
                            (p.position.y-10*density)/(size.height-10*density)) else null
                        val old=held[id]
                        if(old!=note) {
                            if(old!=null) {touchNow(id,null,0f);held.remove(id)}
                            if(note!=null) {held[id]=note;touchNow(id,note,if(current.velocity) (.23f+.77f*(p.position.y/size.height).coerceIn(0f,1f)) else .8f)}
                        }
                        p.consume()
                    }
                }
            }
        } finally {held.keys.toList().forEach{touchNow(it,null,0f)}}
    }) {
        val width=size.width/s.zoom
        val top=10*density
        val height=size.height-top
        drawRect(Brush.verticalGradient(listOf(Color(0xFF343D3B),Color(0xFF101615))),size=Size(size.width,top))
        drawRect(Color(0xFF8D2035),Offset(0f,top-3*density),Size(size.width,4*density))
        clipRect(0f,top,size.width,size.height) {
            fun drawKey(k:Key) {
                val x=(k.left-s.position)*width
                if(x+k.width*width<0 || x>size.width)return
                val down=animation.getValue(k.note.midi).value
                val black=k.note.black
                val h=if(black)height*.64f else height
                val w=k.width*width
                val y=top+down*2*density
                if(black) {
                    drawRoundRect(Color.Black,Offset(x+2*density,y+3*density),Size(w,h),cornerRadius=androidx.compose.ui.geometry.CornerRadius(2*density))
                    drawRect(Brush.horizontalGradient(listOf(Color(0xFF303639),Color(0xFF080B0C),Color(0xFF242A2C)),x,x+w),Offset(x,y),Size(w,h-4*density))
                    drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha=.16f-down*.09f),Color.Transparent),y,y+h*.5f),Offset(x+1*density,y),Size(w-2*density,h*.65f))
                    drawRect(Color(0xFF070A0B),Offset(x+2*density,y+h-12*density),Size((w-4*density).coerceAtLeast(1f),8*density))
                } else {
                    drawRect(Brush.verticalGradient(listOf(Color(0xFFCBC8BF),if(down>.01f)lerp(Color(0xFFFFFDF2),Color(0xFFC8C4B8),down) else Color(0xFFFFFDF2),Color(0xFFE6E0D0)),top,size.height),Offset(x+.7f,y),Size(w-1.4f,h))
                    drawLine(Color(0xFF88897E),Offset(x,y),Offset(x,size.height),1f)
                    drawRect(Color(0xFFB7B3A7),Offset(x+1,size.height-5*density+down*2*density),Size(w-2,5*density))
                    if(k.note.midi==60)drawCircle(Gold,3*density,Offset(x+w/2,top+h*.76f))
                }
                if(down>0)drawRect(Color.Black.copy(alpha=down*.07f),Offset(x,y),Size(w,h))
                if(k.note.visible(s.labels)) {
                    val text=k.note.label(s.notation,s.accidentals)
                    paint.textSize=minOf(if(black)11*density else 14*density,w*.55f)
                    while(paint.measureText(text)>w*.9f && paint.textSize>3*density)paint.textSize-=1f
                    paint.color=if(black) android.graphics.Color.rgb(209,202,184) else android.graphics.Color.rgb(90,91,81)
                    drawIntoCanvas {it.nativeCanvas.drawText(text,x+w/2,top+h-(if(black)19 else 20)*density+down*2*density,paint)}
                }
            }
            Piano.keys.filterNot{it.note.black}.forEach(::drawKey)
            Piano.keys.filter{it.note.black}.forEach(::drawKey)
        }
    }
}
