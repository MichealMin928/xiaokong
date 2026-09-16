package com.airgesture.app.voice

import org.junit.Assert.*
import org.junit.Test
import com.airgesture.app.command.CommandKind
import com.airgesture.app.session.*

class VoiceCommandEngineTest {
    private fun event(word:String,time:Long=1000)=KeywordEvent(word,time,.2f,12)
    @Test fun quietDirectModeNeverRequiresWakeAndSupportsGestureAliases(){
        val e=VoiceCommandEngine(VoiceCommandConfig(activation=VoiceActivation.DIRECT))
        assertEquals(CommandKind.START_MOUSE,e.accept(event("开启手势"),ControlMode.VOICE_ONLY,1000).command?.kind)
        assertEquals(CommandKind.SWIPE_UP,e.accept(event("上滑",1800),ControlMode.VISUAL_MOUSE,1800).command?.kind)
        assertEquals(CommandKind.STOP_MOUSE,e.accept(event("关闭手势",1850),ControlMode.NUMBER_SELECT,1850).command?.kind)
        assertFalse(e.accept(event("你好助手",2600),ControlMode.VOICE_ONLY,2600).awakened)
        assertFalse(e.listening(2601))
    }
    @Test fun unselectedWakePhraseDoesNotAuthorizeAnAction(){
        val e=VoiceCommandEngine(VoiceCommandConfig(wakeWord="小空小空"))
        assertFalse(e.accept(event("你好助手"),ControlMode.VOICE_ONLY,1000).awakened)
        assertEquals("NEEDS_WAKE_WORD",e.accept(event("打开手势",2000),ControlMode.VOICE_ONLY,2000).reason)
        assertTrue(e.accept(event("小空小空",3000),ControlMode.VOICE_ONLY,3000).awakened)
        assertEquals(CommandKind.START_MOUSE,e.accept(event("打开手势",3200),ControlMode.VOICE_ONLY,3200).command?.kind)
    }
    @Test fun wakeSensitivityDoesNotLoosenCommandThreshold(){
        val e=VoiceCommandEngine(VoiceCommandConfig(wakeThreshold=.12f,keywordThreshold=.35f))
        assertTrue(e.accept(event("你好助手").copy(confidence=.2f),ControlMode.VOICE_ONLY,1000).awakened)
        assertEquals("THRESHOLD",e.accept(event("上滑",2000).copy(confidence=.2f),ControlMode.VOICE_ONLY,2000).reason)
    }
    @Test fun showChoicesBypassesWakeButDoesNotAuthorizeNavigationOrClick(){
        for(word in listOf("编号","显示编号","显示按钮","网格","屏幕分区","分区选择")){
            val e=VoiceCommandEngine(VoiceCommandConfig())
            val d=e.accept(event(word),ControlMode.VOICE_ONLY,1000)
            assertTrue(d.command?.kind in setOf(CommandKind.SHOW_NUMBERS,CommandKind.SHOW_GRID))
            assertFalse(e.listening(1001))
            assertEquals("NEEDS_WAKE_WORD",e.accept(event("上滑",1800),ControlMode.NUMBER_SELECT,1800).reason)
            assertNull(e.accept(event("三号",2600),ControlMode.VOICE_ONLY,2600).command)
        }
    }
    @Test fun pausedAndLowConfidenceCannotOpenSelection(){
        for(word in listOf("显示按钮","屏幕分区","编号","网格")){
            val e=VoiceCommandEngine(VoiceCommandConfig())
            assertEquals("PAUSED",e.accept(event(word),ControlMode.PAUSED,1000).reason)
            assertEquals("THRESHOLD",e.accept(event(word,1500).copy(decoderThresholdPassed=false),ControlMode.VOICE_ONLY,1500).reason)
        }
    }
    @Test fun eachSelectedWakePhraseOpensOneBoundedListeningWindow(){
        for(word in VoiceCommandEngine.wakeWords){
            val e=VoiceCommandEngine(VoiceCommandConfig(wakeWord=word))
            assertFalse(e.listening(0))
            assertTrue(e.accept(event(word),ControlMode.VOICE_ONLY,1000).awakened)
            assertTrue(e.listening(1001))
            assertEquals(CommandKind.SWIPE_UP,e.accept(event("上滑",1300),ControlMode.VOICE_ONLY,1300).command?.kind)
            assertFalse(e.listening(1301))
            assertEquals("NEEDS_WAKE_WORD",e.accept(event("下滑",2400),ControlMode.VOICE_ONLY,2400).reason)
        }
    }
    @Test fun silenceClosesListeningAndInvalidWakeDoesNotExtendIt(){
        val e=VoiceCommandEngine(VoiceCommandConfig())
        e.accept(event("你好助手"),ControlMode.VOICE_ONLY,1000)
        assertEquals("THRESHOLD",e.accept(event("你好助手",5900).copy(decoderThresholdPassed=false),ControlMode.VOICE_ONLY,5900).reason)
        assertTrue(e.listening(6000));assertFalse(e.listening(6001))
        assertEquals("NEEDS_WAKE_WORD",e.accept(event("上滑",6100),ControlMode.VOICE_ONLY,6100).reason)
    }
    @Test fun returningAudioFocusRevokesOutstandingAuthorization(){
        val e=VoiceCommandEngine(VoiceCommandConfig())
        e.accept(event("你好助手"),ControlMode.VOICE_ONLY,1000)
        e.cancelWake()
        assertFalse(e.listening(1100))
        assertEquals("NEEDS_WAKE_WORD",e.accept(event("主页",1200),ControlMode.VOICE_ONLY,1200).reason)
        e.accept(event("你好助手",1400),ControlMode.VOICE_ONLY,1400)
        assertEquals(CommandKind.HOME,e.accept(event("主页",1500),ControlMode.VOICE_ONLY,1500).command?.kind)
    }
    @Test fun staleWakeCannotStartMediaListening(){
        val e=VoiceCommandEngine(VoiceCommandConfig())
        assertEquals("STALE",e.accept(event("你好助手"),ControlMode.VOICE_ONLY,1500).reason)
        assertFalse(e.listening(1500))
        assertEquals("UNKNOWN",e.accept(event("你好",1600),ControlMode.VOICE_ONLY,1600).reason)
        assertFalse(e.listening(1600))
    }
    @Test fun wakeAuthorizesOneCommandOnly(){
        val e=VoiceCommandEngine(VoiceCommandConfig())
        assertEquals("NEEDS_WAKE_WORD",e.accept(event("主页"),ControlMode.VOICE_ONLY,1000).reason)
        assertTrue(e.accept(event("你好助手",1100),ControlMode.VOICE_ONLY,1100).awakened)
        assertEquals(CommandKind.HOME,e.accept(event("主页",1300),ControlMode.VOICE_ONLY,1300).command?.kind)
        assertEquals("NEEDS_WAKE_WORD",e.accept(event("上滑",2500),ControlMode.VOICE_ONLY,2500).reason)
    }
    @Test fun wakeExpiresAndStaleOrFutureIsRejected(){
        val e=VoiceCommandEngine(VoiceCommandConfig())
        e.accept(event("你好助手"),ControlMode.VOICE_ONLY,1000)
        assertEquals("NEEDS_WAKE_WORD",e.accept(event("上滑",6001),ControlMode.VOICE_ONLY,6001).reason)
        assertEquals("STALE",e.accept(event("上滑",6500),ControlMode.VOICE_ONLY,6751).reason)
        assertEquals("STALE",e.accept(event("返回",8000),ControlMode.VOICE_ONLY,7900).reason)
    }
    @Test fun cancellationBypassesCooldownAndWake(){
        val e=VoiceCommandEngine(VoiceCommandConfig(activation=VoiceActivation.DIRECT))
        assertNotNull(e.accept(event("鼠标"),ControlMode.VOICE_ONLY,1000).command)
        assertEquals("COOLDOWN",e.accept(event("上滑",1200),ControlMode.VISUAL_MOUSE,1200).reason)
        assertEquals(CommandKind.STOP_MOUSE,e.accept(event("关闭鼠标",1300),ControlMode.VISUAL_MOUSE,1300).command?.kind)
        assertEquals(CommandKind.PAUSE_CONTROL,e.accept(event("暂停控制",1400),ControlMode.VOICE_ONLY,1400).command?.kind)
    }
    @Test fun pausedNeverLeaksActions(){
        for(word in listOf("一","上滑","下滑","返回","主页","鼠标","编号","网格","取消","关闭鼠标")){
            val e=VoiceCommandEngine(VoiceCommandConfig(activation=VoiceActivation.DIRECT))
            assertNull(word,e.accept(event(word),ControlMode.PAUSED,1000).command)
        }
        assertEquals(CommandKind.RESUME_CONTROL,VoiceCommandEngine(VoiceCommandConfig()).accept(event("恢复控制"),ControlMode.PAUSED,1000).command?.kind)
    }
    @Test fun numbersOnlyWithinExplicitSelection(){
        VoiceCommandEngine.numbers.forEachIndexed{i,word->
            val e=VoiceCommandEngine(VoiceCommandConfig())
            assertNull(e.accept(event(word),ControlMode.VOICE_ONLY,1000).command)
            assertEquals(i+1,e.accept(event(word,2000),ControlMode.NUMBER_SELECT,2000).command?.number)
        }
    }
    @Test fun decoderThresholdCannotBeInventedOrBypassed(){
        val e=VoiceCommandEngine(VoiceCommandConfig(activation=VoiceActivation.DIRECT))
        assertEquals("THRESHOLD",e.accept(event("上滑").copy(confidence=Float.NaN),ControlMode.VOICE_ONLY,1000).reason)
        assertEquals("THRESHOLD",e.accept(event("上滑",1100).copy(decoderThresholdPassed=false),ControlMode.VOICE_ONLY,1100).reason)
        assertNotNull(e.accept(event("上滑",1200),ControlMode.VOICE_ONLY,1200).command)
    }
    @Test fun everyBasicCommandHasAnExplicitMapping(){
        val words=listOf("上滑","下滑","返回","主页","鼠标","关闭鼠标","编号","网格","取消","暂停控制","恢复控制")
        val kinds=listOf(CommandKind.SWIPE_UP,CommandKind.SWIPE_DOWN,CommandKind.BACK,CommandKind.HOME,CommandKind.START_MOUSE,CommandKind.STOP_MOUSE,CommandKind.SHOW_NUMBERS,CommandKind.SHOW_GRID,CommandKind.CANCEL,CommandKind.PAUSE_CONTROL,CommandKind.RESUME_CONTROL)
        words.zip(kinds).forEach{(word,kind)->
            val mode=when(kind){CommandKind.STOP_MOUSE->ControlMode.VISUAL_MOUSE;CommandKind.CANCEL->ControlMode.GRID_SELECT;CommandKind.RESUME_CONTROL->ControlMode.PAUSED;else->ControlMode.VOICE_ONLY}
            assertEquals(kind,VoiceCommandEngine(VoiceCommandConfig(activation=VoiceActivation.DIRECT)).accept(event(word),mode,1000).command?.kind)
        }
    }
    @Test fun selectionOnlyAuthorizesNumbersNotUnwokenNavigation(){
        val e=VoiceCommandEngine(VoiceCommandConfig())
        assertEquals("NEEDS_WAKE_WORD",e.accept(event("主页"),ControlMode.NUMBER_SELECT,1000).reason)
        assertEquals(5,e.accept(event("五",1800),ControlMode.NUMBER_SELECT,1800).command?.number)
        assertEquals("NO_CHANGE",e.accept(event("取消",2600),ControlMode.VOICE_ONLY,2600).reason)
    }
    @Test fun noHandTimeoutUsesLastActualHandAndCanBeDisabled(){
        val m=ControlModeManager(5000);m.enter(ControlMode.VISUAL_MOUSE,1000);m.hand(true,4000);m.hand(false,7000)
        assertNull(m.expiry(8999));assertNotNull(m.expiry(9000))
        val never=ControlModeManager(0);never.enter(ControlMode.VISUAL_MOUSE,0);assertNull(never.expiry(999999))
        m.enter(ControlMode.NUMBER_SELECT,10000);assertNull(m.expiry(29999));assertNotNull(m.expiry(30000))
        m.enter(ControlMode.VOICE_ONLY,30000);assertNull(m.expiry(999999))
    }
    @Test fun digitDecoderRequiresOneExactNumber(){
        assertEquals(11,DigitRecognizer.parse("shí yī"));assertEquals(5,DigitRecognizer.parse("wǔ hào"));assertEquals(20,DigitRecognizer.parse("xuǎn zé èr shí hào"))
        for(text in listOf("wǒ","shàng huá","yī èr","shí yī shí","hello","", "xiǎo kōng"))assertNull(text,DigitRecognizer.parse(text))
    }
}
