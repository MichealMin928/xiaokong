package com.airgesture.app

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.airgesture.app.ui.*
import com.airgesture.app.voice.*

class SettingsActivity:ComponentActivity(){
    override fun onCreate(state:Bundle?){super.onCreate(state);render()}
    private fun render(){
        val root=page("设置",true);val store=VoiceSettings(this);val config=store.load()
        feature(root,"识别调节与测试","免唤醒、中文唤醒词、灵敏度与录音兼容",AppIcon.VOICE){startActivity(Intent(this,VoiceSettingsActivity::class.java))}
        feature(root,"全部语音指令","回到桌面、连续翻页、显示按钮与数字",AppIcon.VOICE){startActivity(Intent(this,VoiceCommandsActivity::class.java))}
        section(root,"手势控制")
        val seconds=if(config.mouseSleepMs==0L)"不自动休眠" else "无手 ${config.mouseSleepMs/1000} 秒后关闭"
        feature(root,"手势自动休眠",seconds,AppIcon.POINTER){
            val values=longArrayOf(5000,10000,30000,0)
            AlertDialog.Builder(this).setTitle("多久没有手后关闭手势开关").setSingleChoiceItems(arrayOf("5 秒","10 秒（推荐）","30 秒","不自动休眠"),values.indexOf(config.mouseSleepMs)){dialog,index->store.save(config.copy(mouseSleepMs=values[index]));dialog.dismiss();render()}.show()
        }
        note(root,"设置立即生效。手势休眠会关闭手势开关，语音仍可使用。暂停时只执行恢复控制；结束控制会关闭麦克风。")
        section(root,"使用与保障")
        feature(root,"手势工具","校准、动作训练与应用映射",AppIcon.HAND){startActivity(Intent(this,ToolsActivity::class.java))}
        feature(root,"客户与问题反馈","本机保存记录，可导出交给开发者",AppIcon.RECORD){startActivity(Intent(this,FeedbackActivity::class.java))}
        feature(root,"使用指南","语音命令、手势和点击方式",AppIcon.HAND){startActivity(Intent(this,HelpActivity::class.java))}
        feature(root,"权限管理","麦克风、相机、悬浮窗与无障碍",AppIcon.SHIELD){startActivity(Intent(this,SetupActivity::class.java))}
        feature(root,"运行状态","查看麦克风、相机和识别耗时",AppIcon.RECORD){startActivity(Intent(this,PowerDiagnosticsActivity::class.java))}
        feature(root,"隐私与开源许可","设备内处理 · 无网络权限",AppIcon.SHIELD){startActivity(Intent(this,PrivacyActivity::class.java))}
        note(root,"小空 · ${BuildConfig.VERSION_NAME}\n社区预览版")
    }
}
class ToolsActivity:ComponentActivity(){override fun onCreate(state:Bundle?){super.onCreate(state);val root=page("手势与阅读",true)
    note(root,"这些工具保留原来的操作方式。持续手势模式会常开相机；日常使用建议从首页开始语音控制。")
    feature(root,"隔空操作","四向翻页、返回、桌面与鼠标",AppIcon.HAND){startActivity(Intent(this,SceneActivity::class.java))}
    feature(root,"鼠标校准","调整瞄准范围，检查食指与拇指点击",AppIcon.POINTER){startActivity(Intent(this,MainActivity::class.java))}
    feature(root,"眼控阅读 · 实验功能","先校准，再检查视线翻页",AppIcon.EYE){startActivity(Intent(this,EyeActivity::class.java))}
    feature(root,"动作训练","按引导采集，查看记录与回放",AppIcon.RECORD){startActivity(Intent(this,TrainingActivity::class.java))}
    feature(root,"应用与手势映射","管理已有应用场景及手势动作",AppIcon.SETTINGS){startActivity(Intent(this,AppSettingsActivity::class.java))}
    button(root,"停止全部控制"){stopService(Intent(this,com.airgesture.app.service.GestureForegroundService::class.java))}
}}
class HelpActivity:ComponentActivity(){override fun onCreate(state:Bundle?){super.onCreate(state);val root=page("使用指南",true)
    val config=VoiceSettings(this).load()
    section(root,"两个开关，分别控制")
    note(root,"首页语音和手势可单独开关。语音开启时，说「开启手势」或「打开手势」启用手势；说「关闭手势」释放相机。关闭语音不会关闭已经开启的手势。\n当前语音方式：${if(config.activation==VoiceActivation.DIRECT)"直接说指令，无需唤醒" else "先说「${config.wakeWord}」"}。可在首页或识别调节中切换。")
    section(root,"显示按钮与语音操作")
    button(root,"查看全部语音指令"){startActivity(Intent(this,VoiceCommandsActivity::class.java))}
    note(root,"回到桌面：说「回到桌面」「返回桌面」「桌面」或「主页」。\n显示按钮、显示编号、编号、按钮，都可显示当前真实按钮；分区选择、屏幕分区、网格都可显示有按钮的区域。\n直接模式说完一句，稍停一下，可继续说上滑、下滑、左滑、右滑、返回等。唤醒模式每次操作前先说「${config.wakeWord}」；显示选择无需唤醒。")
    section(root,"点进你想看的内容")
    note(root,"显示按钮：给真实可点击控件描边并标号。等提示数字识别准备好，再说「五号」或「点击五号」。\n分区选择：只标出含按钮的区域。先说区域号，再说里面的按钮号；不点击空格中心。\n滚动或切换页面后请重新说「显示按钮」。目标自身变化时会拒绝旧编号，避免点错。页面未提供按钮时可用隔空鼠标。\n鼠标：单独伸出食指瞄准；拇指向食指靠近、打开，再靠近一次确认。不需要碰到。")
    section(root,"一只手也能翻页")
    note(root,"四个长手指朝上，整只手向上移：上滑。\n食指和中指朝上，整只手向下移：下滑。\n五指张开，向左移：左滑。\n食指和中指伸出，向右移：右滑。\n四个指尖朝左或右，横向推：返回。\n拇指和小指比「6」短暂停留：回桌面。\n单独伸拇指，连续向上移动两次：点赞；向下移动两次：不喜欢。不同应用支持情况可能不同。")
    section(root,"让操作更稳定")
    note(root,"保持手掌在前摄范围内，食指瞄准时放松其他手指。视频外放盖住说话声时，从识别调节或通知开启外放辅助，辅助期间会临时压低媒体声音。关闭辅助、暂停、锁屏或结束控制后交还声音控制；部分播放器可能暂停，需自行继续播放。唤醒后也会为一个命令临时降音，超时恢复。大音量和远距离仍可能漏听，必要时降低音量或使用耳机。语音未识别时先重复完整命令；数字没反应可加「号」。\n没有举手时相机会自动关闭。麦克风被通话或其他录音占用时暂停控制，回到应用手动恢复。锁屏会结束整个会话。")
}}
