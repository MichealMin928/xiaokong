package com.airgesture.app.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.*
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

data class AudioDiagnostics(val state:String="OFF",val source:String="VOICE_RECOGNITION",
    val aecSupported:Boolean=false,val aecEnabled:Boolean=false,val nsSupported:Boolean=false,val nsEnabled:Boolean=false,
    val inferenceCount:Long=0,val inferenceMs:Long=0,val audioMs:Long=0,val error:String?=null,val digitsActive:Boolean=false,val digitInferenceCount:Long=0,val digitInferenceMs:Long=0,
    val inputLevelDb:Float=-120f,val inputPeakDb:Float=-120f,
    val droppedAudioMs:Long=0,val inputDelayMs:Long=0,val decoder:String="KEYWORDS",val unmatchedUtterances:Long=0)

/** Explicit session only. Direct mode uses one complete-utterance decoder; wake mode uses KWS. No audio files or network. */
class AudioInputManager(private val context:Context,private val config:VoiceCommandConfig,
    private val onKeyword:(KeywordEvent)->Unit,private val onStatus:(AudioDiagnostics)->Unit):AutoCloseable {
    companion object { private val microphoneOwner=java.util.concurrent.locks.ReentrantLock(true) }
    private val main=Handler(Looper.getMainLooper())
    private val worker=Executors.newSingleThreadExecutor()
    private val capture=Executors.newSingleThreadExecutor()
    private data class Chunk(val pcm:ShortArray,val epoch:Long,val capturedAt:Long,val generation:Long)
    private val chunks=java.util.concurrent.ArrayBlockingQueue<Chunk>(40)
    private val generation=java.util.concurrent.atomic.AtomicLong(0)
    private val dropped=java.util.concurrent.atomic.AtomicLong(0)
    @Volatile private var captureFailure:String?=null
    private val stopped=AtomicBoolean(false)
    private val closed=AtomicBoolean(false)
    @Volatile private var recorder:AudioRecord?=null
    private val vocabularyEpoch=java.util.concurrent.atomic.AtomicLong(0)
    @Volatile var vocabulary=VoiceVocabulary.COMMANDS
        set(value){if(field!=value){field=value;vocabularyEpoch.incrementAndGet()}}
    private var started=false
    private fun publish(value:AudioDiagnostics,epoch:Long?=null){main.post{if(!closed.get() && (epoch==null || epoch==vocabularyEpoch.get()) && !(stopped.get() && value.state in setOf("ACTIVE","STARTING")))onStatus(value)}}
    fun start(){
        check(!started);started=true
        if(ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){publish(AudioDiagnostics("ERROR",error="请先允许麦克风"));return}
        worker.execute{
            var direct:DirectCommandRecognizer?=null;var kws:KeywordSpottingEngine?=null;var digits:DigitRecognizer?=null;var aec:AcousticEchoCanceler?=null;var ns:NoiseSuppressor?=null
            val audioManager=context.getSystemService(AudioManager::class.java)
            val unprocessed=!config.noiseProcessing && audioManager.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED)=="true"
            val source=if(unprocessed)MediaRecorder.AudioSource.UNPROCESSED else if(config.noiseProcessing && config.communicationSource)MediaRecorder.AudioSource.VOICE_COMMUNICATION else MediaRecorder.AudioSource.VOICE_RECOGNITION
            var status=AudioDiagnostics("STARTING",decoder=if(config.activation==VoiceActivation.DIRECT)"UTTERANCE" else "KEYWORDS",source=if(unprocessed)"UNPROCESSED" else if(source==MediaRecorder.AudioSource.VOICE_COMMUNICATION)"VOICE_COMMUNICATION" else "VOICE_RECOGNITION")
            // A previous session must release its recorder before a new settings/start request owns it.
            microphoneOwner.lock()
            try{
                if(stopped.get())return@execute
                publish(status)
                if(config.activation==VoiceActivation.DIRECT)direct=DirectCommandRecognizer(context,config.wakeWord)
                else kws=KeywordSpottingEngine(context,config)
                if(stopped.get())return@execute
                val min=AudioRecord.getMinBufferSize(16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT)
                check(min>0){"当前设备不支持 16 kHz 麦克风输入"}
                val input=AudioRecord.Builder().setAudioSource(source)
                    .setAudioFormat(AudioFormat.Builder().setSampleRate(16000).setChannelMask(AudioFormat.CHANNEL_IN_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                    .setBufferSizeInBytes(maxOf(min*2,16000)).build()
                recorder=input;check(input.state==AudioRecord.STATE_INITIALIZED){"麦克风初始化失败"}
                val aecSupported=AcousticEchoCanceler.isAvailable();val nsSupported=NoiseSuppressor.isAvailable()
                if(aecSupported)aec=runCatching{AcousticEchoCanceler.create(input.audioSessionId)?.also{it.enabled=config.noiseProcessing}}.getOrNull()
                if(nsSupported)ns=runCatching{NoiseSuppressor.create(input.audioSessionId)?.also{it.enabled=config.noiseProcessing}}.getOrNull()
                status=status.copy(state="ACTIVE",aecSupported=aecSupported,aecEnabled=aec?.enabled==true,nsSupported=nsSupported,nsEnabled=ns?.enabled==true)
                fun interruptInput(){
                    if(!stopped.getAndSet(true)){
                        publish(status.copy(state="INTERRUPTED",error="麦克风被其他应用使用，结束通话或录音后回到小空继续"))
                        runCatching{input.stop()}
                    }
                }
                // Only our own silenced state proves loss of input. Other clients may be
                // inactive/silenced; their mere presence must not permanently stop our mic.
                if(Build.VERSION.SDK_INT>=29){
                    input.registerAudioRecordingCallback(ContextCompat.getMainExecutor(context),object:AudioManager.AudioRecordingCallback(){
                        override fun onRecordingConfigChanged(configs:MutableList<AudioRecordingConfiguration>){
                            if(!stopped.get() && configs.any{it.clientAudioSessionId==input.audioSessionId && it.isClientSilenced}){
                                interruptInput()
                            }
                        }
                    })
                }
                if(stopped.get())return@execute
                input.startRecording();check(input.recordingState==AudioRecord.RECORDSTATE_RECORDING){"麦克风未开始工作"}
                publish(status)
                // Reading must continue while the numeric model/stream is prepared. A bounded
                // queue absorbs short stalls; discontinuities reset both decoders, never replay
                // an old command when the phone catches up after a long stall.
                capture.execute{
                    android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO)
                    try{while(!stopped.get()){
                        val readEpoch=vocabularyEpoch.get();val pcm=ShortArray(800)
                        val n=input.read(pcm,0,pcm.size,AudioRecord.READ_BLOCKING)
                        if(n<0)throw IllegalStateException("麦克风中断 ($n)")
                        if(n==0 || readEpoch!=vocabularyEpoch.get())continue
                        if(chunks.remainingCapacity()==0){dropped.addAndGet(chunks.size*50L);chunks.clear();generation.incrementAndGet()}
                        chunks.offer(Chunk(if(n==pcm.size)pcm else pcm.copyOf(n),readEpoch,SystemClock.uptimeMillis(),generation.get()))
                    }}catch(e:Exception){if(!stopped.get())captureFailure=e.message ?: "麦克风读取中断"}
                }
                var lastInputAt=SystemClock.uptimeMillis();var samples=0L;var lastReport=0L;var appliedEpoch=-1L;var appliedGeneration=-1L
                var energy=0.0;var levelSamples=0L;var peak=0
                while(!stopped.get()){
                    captureFailure?.let{throw IllegalStateException(it)}
                    val chunk=chunks.poll(100,java.util.concurrent.TimeUnit.MILLISECONDS)
                    if(chunk==null){check(SystemClock.uptimeMillis()-lastInputAt<3000){"麦克风超过 3 秒未返回数据，请点恢复控制"};continue}
                    lastInputAt=SystemClock.uptimeMillis()
                    val readEpoch=chunk.epoch;val pcm=chunk.pcm;val n=pcm.size
                    if(readEpoch!=vocabularyEpoch.get())continue
                    if(SystemClock.uptimeMillis()-chunk.capturedAt>600){dropped.addAndGet(n*1000L/16000);appliedGeneration=-1;continue}
                    if(appliedEpoch!=readEpoch){
                        kws?.setVocabulary(vocabulary,force=true);direct?.reset();digits?.close();digits=null;appliedEpoch=readEpoch
                    }else if(appliedGeneration!=chunk.generation){kws?.setVocabulary(vocabulary,force=true);direct?.reset();digits?.reset()}
                    appliedGeneration=chunk.generation
                    if(direct==null && vocabulary==VoiceVocabulary.SELECTION && digits==null)digits=DigitRecognizer(context)
                    else if(vocabulary!=VoiceVocabulary.SELECTION && digits!=null){digits.close();digits=null}
                    val before=direct?.inferenceMs ?: kws?.inferenceMs ?: 0L
                    val floats=FloatArray(n){pcm[it]/32768f}
                    for(i in 0 until n){val sample=pcm[i].toInt();energy+=sample.toDouble()*sample;peak=maxOf(peak,kotlin.math.abs(sample))};levelSamples+=n
                    val results=direct?.accept(floats,vocabulary) ?: kws?.accept(floats) ?: emptyList();samples+=n
                    val digitBefore=digits?.inferenceMs ?: 0L
                    val numeric=digits?.accept(floats) ?: emptyList()
                    val fresh=SystemClock.uptimeMillis()-chunk.capturedAt<=if(direct!=null)1500 else 600
                    for(word in results){val event=KeywordEvent(word,SystemClock.uptimeMillis(),if(word==config.wakeWord)config.wakeThreshold else config.keywordThreshold,(direct?.inferenceMs ?: kws?.inferenceMs ?: 0L)-before,source=if(direct!=null)KeywordSource.COMMAND_DECODER else KeywordSource.KWS);main.post{if(fresh && !stopped.get() && readEpoch==vocabularyEpoch.get())onKeyword(event)}}
                    for(word in numeric){val event=KeywordEvent(word,SystemClock.uptimeMillis(),config.keywordThreshold,(digits?.inferenceMs ?: 0L)-digitBefore,source=KeywordSource.DIGIT_DECODER);main.post{if(fresh && !stopped.get() && readEpoch==vocabularyEpoch.get() && vocabulary==VoiceVocabulary.SELECTION)onKeyword(event)}}
                    val now=SystemClock.uptimeMillis()
                    if(now-lastReport>=1000){lastReport=now
                        fun db(value:Double)=(20*kotlin.math.log10((value/32768).coerceAtLeast(.000001))).toFloat()
                        publish(status.copy(inferenceCount=direct?.inferenceCount ?: kws?.inferenceCount ?: 0L,inferenceMs=direct?.inferenceMs ?: kws?.inferenceMs ?: 0L,audioMs=samples*1000/16000,digitsActive=vocabulary==VoiceVocabulary.SELECTION && (direct!=null || digits!=null),digitInferenceCount=digits?.inferenceCount ?: 0L,digitInferenceMs=digits?.inferenceMs ?: 0L,
                            unmatchedUtterances=direct?.unmatchedUtterances ?: 0L,inputLevelDb=db(kotlin.math.sqrt(energy/levelSamples.coerceAtLeast(1))),inputPeakDb=db(peak.toDouble()),
                            droppedAudioMs=dropped.get(),inputDelayMs=now-chunk.capturedAt),readEpoch)
                        energy=0.0;levelSamples=0;peak=0
                    }
                }
            }catch(e:Exception){if(!stopped.get())publish(status.copy(state="ERROR",error=e.message ?: "语音识别已停止"))}
            finally{
                stopped.set(true)
                // A cleanup failure must never strand ownership and block every future restart.
                try{
                    runCatching{recorder?.stop()};capture.shutdown()
                    runCatching{capture.awaitTermination(2,java.util.concurrent.TimeUnit.SECONDS)}
                    runCatching{aec?.release()};runCatching{ns?.release()};runCatching{recorder?.release()}
                    recorder=null;chunks.clear()
                    runCatching{digits?.close()};runCatching{kws?.close()};runCatching{direct?.close()}
                }finally{microphoneOwner.unlock()}
            }
        }
    }
    override fun close(){closed.set(true);stopped.set(true);runCatching{recorder?.stop()};capture.shutdown();worker.shutdown()}
}
