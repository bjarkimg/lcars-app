package io.starfleet.lcars.app.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class ScanVoice(context: Context) : TextToSpeech.OnInitListener {
    private var ready = false
    private val tts = TextToSpeech(context.applicationContext, this)

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            tts.language = Locale.US
            tts.setSpeechRate(0.92f)
        }
    }

    fun scanningComplete() {
        if (!ready) return
        tts.stop()
        tts.speak("Scanning complete", TextToSpeech.QUEUE_FLUSH, null, "scan-complete")
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }
}
