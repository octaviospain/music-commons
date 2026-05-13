package net.transgressoft.commons.media.player

import net.transgressoft.commons.music.audio.ArbitraryAudioFile.realAudioFile
import net.transgressoft.commons.music.audio.AudioFileTagType
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.property.Arb
import io.kotest.property.arbitrary.next
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem

class SpiFormatConversionTest : StringSpec({

    "OGG format conversion produces PCM data" {
        val realAudioPath = Arb.realAudioFile(AudioFileTagType.VORBIS_COMMENT).next()
        val file = realAudioPath.toFile()

        AudioSystem.getAudioInputStream(file).use { rawStream ->
            println("OGG base format: ${rawStream.format}")

            val targetFormat =
                AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    rawStream.format.sampleRate,
                    16,
                    rawStream.format.channels,
                    rawStream.format.channels * 2,
                    rawStream.format.sampleRate,
                    false
                )

            val pcmStream = AudioSystem.getAudioInputStream(targetFormat, rawStream)
            val buffer = ByteArray(8192)
            var totalBytes = 0
            var bytesRead = pcmStream.read(buffer)
            while (bytesRead != -1) {
                if (bytesRead > 0) totalBytes += bytesRead
                bytesRead = pcmStream.read(buffer)
            }
            pcmStream.close()
            println("OGG PCM bytes produced: $totalBytes")
            totalBytes shouldBeGreaterThan 0
        }
    }
})