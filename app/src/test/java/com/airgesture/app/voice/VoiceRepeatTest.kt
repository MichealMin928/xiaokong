package com.airgesture.app.voice

import com.airgesture.app.command.CommandKind
import com.airgesture.app.session.ControlMode
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class VoiceRepeatTest {
    @Test fun repeatedUtterancesContinueAfterEveryAction(){
        val engine=VoiceCommandEngine(VoiceCommandConfig(activation=VoiceActivation.DIRECT))
        repeat(100){i->val t=1000L+i*1000
            assertEquals(CommandKind.SWIPE_UP,engine.accept(KeywordEvent("上滑",t,.2f,0),ControlMode.VOICE_ONLY,t).command?.kind)
        }
    }
    @Test fun pauseReportsEveryAttemptAndExplicitResumeKeepsFutureCommandsWorking(){
        val engine=VoiceCommandEngine(VoiceCommandConfig(activation=VoiceActivation.DIRECT))
        repeat(8){i->val t=1000L+i*1000;assertEquals("PAUSED",engine.accept(KeywordEvent("上滑",t,.2f,0),ControlMode.PAUSED,t).reason)}
        assertEquals(CommandKind.RESUME_CONTROL,engine.accept(KeywordEvent("恢复控制",10000,.2f,0),ControlMode.PAUSED,10000).command?.kind)
        assertEquals(CommandKind.SWIPE_UP,engine.accept(KeywordEvent("上滑",11000,.2f,0),ControlMode.VOICE_ONLY,11000).command?.kind)
    }
    @Test fun chineseRecognitionNeverExecutesNegationOrLongerConversations(){
        fun parse(text:String,mode:VoiceVocabulary=VoiceVocabulary.COMMANDS)=VoiceCommandCatalog.parseTextUtterance(text,mode,"你好助手")
        assertEquals("上滑",parse("上滑。"));assertEquals("编号",parse("编号！"));assertEquals("回到桌面",parse("回到桌面"))
        assertEquals("十二",parse("点击12号",VoiceVocabulary.SELECTION));assertEquals("二",parse("两号",VoiceVocabulary.SELECTION))
        for(text in listOf("不要上滑","请不要上滑","我想看看这个编号","上滑下滑","12号","", "今天返回了桌面"))assertNull(text,parse(text))
        for(text in listOf("0号","21号","123号","一号二号","请不要点一号"))assertNull(text,parse(text,VoiceVocabulary.SELECTION))
    }
    @Test fun guideAndBothDecodersHaveExactlyTheSameCommandPhrases(){
        val file=File("src/main/assets/kws/keywords.txt")
        val words=file.readLines().map{it.substringAfter('@')}.filter{it !in VoiceCommandEngine.wakeWords}.toSet()
        assertEquals(words,VoiceCommandCatalog.byPhrase.keys)
        for(entry in VoiceCommandCatalog.entries)entry.phrases.forEach{w->assertEquals(w,VoiceCommandCatalog.parseTextUtterance(w,VoiceVocabulary.COMMANDS,"你好助手"))}
    }
}
