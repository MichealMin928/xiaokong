package com.airgesture.app

import android.content.Intent
import android.os.*
import androidx.activity.ComponentActivity
import com.airgesture.app.session.ControlSessionState
import com.airgesture.app.command.*
import com.airgesture.app.ui.*

class PowerDiagnosticsActivity:ComponentActivity(){override fun onCreate(state:Bundle?){super.onCreate(state);val root=page("运行状态",true)
    val status=note(root,"");val resources=note(root,"");val voice=note(root,"");val level=note(root,"")
    section(root,"识别诊断")
    val last=note(root,"")
    ControlSessionState.status.observe(this){s->
        if(s.running && s.mode in setOf(com.airgesture.app.session.ControlMode.NUMBER_SELECT,com.airgesture.app.session.ControlMode.GRID_SELECT))return@observe
        status.text="${if(s.running)s.mode.label else "未运行"}\n${s.message}"
        resources.text="麦克风：${s.audio.state}\n相机：${if(s.cameraBound)"开启" else "关闭"}　手部模型：${if(s.handModelLoaded)"已加载" else "已释放"}\n本次语音 ${s.voiceMs/1000} 秒 · 相机 ${s.cameraMs/1000} 秒 · 手部模型 ${s.handModelMs/1000} 秒"
        level.text="麦克风峰值：${s.audio.inputPeakDb.toInt()} dBFS\n输入排队 ${s.audio.inputDelayMs} 毫秒 · 过期丢弃 ${s.audio.droppedAudioMs} 毫秒\n说话时数值应变化；持续 -120 表示当前输入接近静音。这里只显示强度，不保存录音。"
        voice.text="外放辅助：${if(s.videoAssist)"开启" else "关闭"}\n临时声音控制：${s.mediaFocus}\n识别方式：${if(s.audio.decoder=="UTTERANCE")"完整短句" else "关键词"}\n录音源：${s.audio.source}\n回声消除：${if(s.audio.aecEnabled)"已启用" else if(s.audio.aecSupported)"未启用" else "设备不支持"}\n降噪：${if(s.audio.nsEnabled)"已启用" else if(s.audio.nsSupported)"未启用" else "设备不支持"}\n关键词推理 ${s.audio.inferenceCount} 次 · ${s.audio.inferenceMs} 毫秒 · 平均 ${if(s.audio.inferenceCount>0)s.audio.inferenceMs/s.audio.inferenceCount else 0} 毫秒\n数字识别：${if(s.audio.digitsActive)"临时开启" else "关闭"}\n手部推理 ${s.handFrames} 帧 · ${s.handInferenceMs} 毫秒 · 平均 ${if(s.handFrames>0)s.handInferenceMs/s.handFrames else 0} 毫秒\n未匹配完整指令：${s.audio.unmatchedUtterances} 次\n累计命令 ${s.commandCount}"
        last.text="最近识别：${s.lastKeyword}\n判定：${s.lastDecision}\n关键词解码阈值：${s.keywordThreshold}（仅唤醒模式使用；不代表单次置信度）\n最近动作耗时：${s.lastActionMs?.let{"$it 毫秒"} ?: "—"}"
    }
    button(root,"恢复语音控制"){ControlSessionState.request(ControlCommand(CommandKind.RESUME_CONTROL,SystemClock.uptimeMillis()))}
    button(root,"仅检查语音识别"){startActivity(Intent(this,VoiceDiagnosticsActivity::class.java))}
    note(root,"累计时长用于检查资源开关，不等于电池耗电量。回声消除已启用也可能听到外放声音。诊断页面不保存音频或画面。")
}}
class PrivacyActivity:ComponentActivity(){override fun onCreate(state:Bundle?){super.onCreate(state);val root=page("隐私与许可",true)
    section(root,"数据留在你的手机")
    note(root,"小空不需要账号，没有广告和云端识别，也不申请网络权限。麦克风声音、摄像头图像在内存中处理，用后释放，不保存原始录音、照片或视频。锁屏、结束控制、退出任务后停止采集。")
    section(root,"只有你要求时才操作")
    note(root,"无障碍服务用于读取前台应用并执行你发出的操作。按钮与分区选择会临时读取可见控件属性、文字和位置，仅保存在内存中，用于标号及点击前复核。按钮选择最多 20 个候选，分区最多 80 个；滚动、切换页面、取消、超时或结束选择会清除，不保存页面文字。第三方应用及系统安全页面可能限制操作。")
    section(root,"保留的数据")
    note(root,"反馈和跟读测试只保存你填写的说明、环境标签、固定指令的识别结果与运行指标；在本机最多保留 100 条，可查看、删除或导出。没有自动上传或在线客服。设置和校准结果保存在应用私有目录。动作训练只在你进入训练并主动采集时，记录手部关键点、时间、判断结果和评价，不记录原始影像或声音。已有训练记录予以保留。最近语音记录在本机保留最多 80 条，可查看与清空，只包含固定命令名、运行指标和动作结果，不包含任意语音转写或页面文字。卸载应用会删除这些本地数据。")
    section(root,"开源组件")
    note(root,"CameraX / AndroidX · Apache 2.0\nMediaPipe · Apache 2.0\nsherpa-onnx 1.13.8 · Apache 2.0\n中文关键词模型：WenetSpeech 3.3M Mobile（官方模型说明标注 Apache 2.0）\nSilero VAD · MIT\nSenseVoiceSmall / 阿里巴巴集团 · FunASR 模型开源协议 v1.1\nONNX Runtime · MIT")
    button(root,"阅读随包许可与来源"){
        val content=runCatching{assets.open("licenses/NOTICE.txt").bufferedReader().use{it.readText()}}.getOrDefault("许可文件尚未打包，请联系版本维护者。")
        android.app.AlertDialog.Builder(this).setTitle("开源许可与来源").setMessage(content).setPositiveButton("关闭",null).show()
    }
    button(root,"查看源码 / 参与项目"){CommunityLinks.open(this,CommunityLinks.PROJECT)}
    note(root,"版本 ${BuildConfig.VERSION_NAME} · 社区预览版\n项目代码使用 Apache-2.0；第三方库及模型遵循各自许可。社区页面由外部浏览器打开，App 不自动上传数据。")
}}
