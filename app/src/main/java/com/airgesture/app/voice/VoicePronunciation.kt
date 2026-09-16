package com.airgesture.app.voice

import android.content.Context

/** Correct orthographic homophones only. No edit-distance or substring matching.
 * The complete pronunciation must belong to a single supported command. */
class VoicePronunciation internal constructor(private val characters:Map<Char,String>,private val commands:Map<String,List<String>>){
    constructor(context:Context):this(
        context.assets.open("speech/command_characters.tsv").bufferedReader().use{r->r.readLines().map{it.split('\t')}.associate{it[0].single() to it[1]}},
        context.assets.open("speech/command_phrases.tsv").bufferedReader().use{r->r.readLines().map{it.split('\t')}.groupBy({it[1]},{it[0]})}
    )
    fun parse(text:String,vocabulary:VoiceVocabulary,wakeWord:String):String? {
        val value=text.replace(Regex("[\\p{P}\\s]"),"")
        if(value.length !in 1..12)return null
        val syllables=StringBuilder()
        for(c in value){val p=characters[c] ?: return null;syllables.append(p)}
        val key=syllables.toString()
        val phrases=commands[key].orEmpty().filter{it !in VoiceCommandEngine.wakeWords || it==wakeWord}
        val kinds=phrases.map{VoiceCommandCatalog.byPhrase[it] ?: it}.distinct()
        if(phrases.isNotEmpty() && kinds.size==1)return phrases.first()
        if(vocabulary==VoiceVocabulary.SELECTION)return DigitRecognizer.parse(key)?.let{VoiceCommandEngine.numbers[it-1]}
        return null
    }
}
