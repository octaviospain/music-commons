package net.transgressoft.commons.media.player

import net.transgressoft.commons.media.util.drainAndCount
import net.transgressoft.commons.music.audio.ArbitraryAudioFile.realAudioFile
import net.transgressoft.commons.music.audio.AudioFileTagType.ID3_V_24
import net.transgressoft.commons.music.audio.AudioFileTagType.VORBIS_COMMENT
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.property.Arb
import io.kotest.property.arbitrary.next
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem

internal class SpiFormatConversionTest : FunSpec({

    context("format conversion via the raw AudioSystem SPI path produces PCM data") {
        withData(
            mapOf(
                "mp3" to ID3_V_24,
                "ogg" to VORBIS_COMMENT
            )
        ) { tagType ->
            val file = Arb.realAudioFile(tagType).next().toFile()

            AudioSystem.getAudioInputStream(file).use { rawStream ->
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

                AudioSystem.getAudioInputStream(targetFormat, rawStream).drainAndCount() shouldBeGreaterThan 0
            }
        }
    }
})