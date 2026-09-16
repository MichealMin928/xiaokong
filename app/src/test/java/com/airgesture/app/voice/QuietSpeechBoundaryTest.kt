package com.airgesture.app.voice

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class QuietSpeechBoundaryTest {
    @Test fun raisedBackgroundStillEndsWordsAndBecomesTheNewFloor(){
        val b=QuietSpeechBoundary();val room=FloatArray(800){.003f};val word=FloatArray(800){if(it%2==0).035f else -.035f}
        // The room becomes louder than the initial estimate. A word must still end
        // at the new room level and the following room frames must not restart it.
        repeat(20){
            assertFalse(b.update(word,false).first);assertTrue(b.update(word,false).first)
            repeat(8){assertFalse(b.update(word,true).second)}
            repeat(4){assertFalse(b.update(room,true).second)}
            assertTrue(b.update(room,true).second);b.reset()
            repeat(10){assertFalse(b.update(room,false).first)}
        }
    }
    @Test fun quietNeverStartsButRepeatedSoftWordsDo(){
        val b=QuietSpeechBoundary();val room=FloatArray(800){.0002f};val word=FloatArray(800){if(it%2==0).008f else -.008f}
        repeat(50){assertFalse(b.update(room,false).first)}
        repeat(40){
            assertFalse(b.update(word,false).first);assertTrue(b.update(word,false).first)
            repeat(4){assertFalse(b.update(room,true).second)}
            assertTrue(b.update(room,true).second);b.reset()
        }
    }
    @Test fun samePronunciationRequiresTheWholeUtteranceAndNumbersStayScoped(){
        val root=File("src/main/assets/speech")
        val m=VoicePronunciation(root.resolve("command_characters.tsv").readLines().map{it.split('\t')}.associate{it[0].single() to it[1]},root.resolve("command_phrases.tsv").readLines().map{it.split('\t')}.groupBy({it[1]},{it[0]}))
        fun parse(t:String,mode:VoiceVocabulary=VoiceVocabulary.COMMANDS)=m.parse(t,mode,"你好助手")
        assertEquals("上滑",parse("上划"));assertEquals("显示编号",parse("显示编好"))
        for(t in listOf("上海","不要上划","我想看看编号","上划下划","一号"))assertNull(t,parse(t))
        assertEquals("一",parse("医号",VoiceVocabulary.SELECTION));assertNull(parse("你好",VoiceVocabulary.SELECTION))
    }
}
