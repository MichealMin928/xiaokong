package com.airgesture.probe;
import android.app.Activity;
import android.os.*;
import android.media.*;
import android.widget.TextView;
public class ProbeActivity extends Activity {
 private volatile boolean stopped=false;
 private volatile AudioRecord input;
 @Override public void onCreate(Bundle state){super.onCreate(state);
  getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
  TextView label=new TextView(this);label.setText("麦克风竞争验收 · 8 秒后关闭\n只读取内存缓冲区，不保存任何录音。");label.setTextSize(22);setContentView(label);
  new Thread(()->{try{
   AudioRecord.Builder builder=new AudioRecord.Builder().setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
    .setAudioFormat(new AudioFormat.Builder().setSampleRate(16000).setChannelMask(AudioFormat.CHANNEL_IN_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
    .setBufferSizeInBytes(16000);
   if(Build.VERSION.SDK_INT>=30)builder.setPrivacySensitive(true);
   input=builder.build();input.startRecording();long end=SystemClock.uptimeMillis()+8000;short[] pcm=new short[1600];
   while(!stopped && SystemClock.uptimeMillis()<end){if(input.read(pcm,0,pcm.length)<0)break;}
  }catch(Exception e){android.util.Log.e("MicProbe",e.toString());}
  finally{if(input!=null){try{input.stop();}catch(Exception ignored){}input.release();input=null;}runOnUiThread(this::finish);}}).start();
 }
 @Override public void onDestroy(){stopped=true;if(input!=null){try{input.stop();}catch(Exception ignored){}}super.onDestroy();}
}
