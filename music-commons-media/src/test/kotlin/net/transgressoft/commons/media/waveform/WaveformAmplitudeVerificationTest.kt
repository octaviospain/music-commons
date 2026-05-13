package net.transgressoft.commons.media.waveform

import net.transgressoft.commons.music.audio.ArbitraryAudioFile.realAudioFile
import net.transgressoft.commons.music.audio.AudioFileTagType.WAV
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.floats.shouldBeGreaterThan
import io.kotest.property.Arb
import io.kotest.property.arbitrary.next
import kotlinx.coroutines.Dispatchers

class WaveformAmplitudeVerificationTest : StringSpec({

    "Waveform produces non-zero amplitudes for WAV" {
        val realAudioPath = Arb.realAudioFile(WAV).next()
        val waveform = ScalableAudioWaveform(1, realAudioPath)
        val amplitudes = waveform.amplitudes(200, 100, Dispatchers.Default)

        val maxAmplitude = amplitudes.maxOrNull() ?: 0f
        println("Max amplitude: $maxAmplitude")
        println("First 10 amplitudes: ${amplitudes.take(10).toList()}")

        maxAmplitude shouldBeGreaterThan 0f
    }
})