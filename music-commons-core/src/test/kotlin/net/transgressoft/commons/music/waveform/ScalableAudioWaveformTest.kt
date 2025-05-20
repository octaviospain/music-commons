package net.transgressoft.commons.music.waveform

import net.transgressoft.commons.music.audio.ArbitraryAudioFile.realAudioFile
import net.transgressoft.commons.music.audio.AudioFileTagType.FLAC
import net.transgressoft.commons.music.audio.AudioFileTagType.ID3_V_24
import net.transgressoft.commons.music.audio.AudioFileTagType.MP4_INFO
import net.transgressoft.commons.music.audio.AudioFileTagType.WAV
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.engine.spec.tempfile
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.next
import java.awt.Color
import javax.imageio.ImageIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class ScalableAudioWaveformTest : FunSpec({

    context("Creates a waveform image from") {
        withData(
            mapOf(
                "a wav file" to Arb.realAudioFile(WAV).next(),
                "a mp3 file" to Arb.realAudioFile(ID3_V_24).next(),
                "a m4aFile" to Arb.realAudioFile(MP4_INFO).next(),
                "a flac file" to Arb.realAudioFile(FLAC).next()
            )
        ) {
            val pngTempFile = tempfile(suffix = ".png")
            ScalableAudioWaveform(1, it).createImage(pngTempFile, Color.RED, Color.BLUE, 780, 335)
            pngTempFile.exists() shouldBe true

            pngTempFile.extension shouldBe "png"
            pngTempFile.length() shouldNotBe null

            val bufferedImage =
                withContext(Dispatchers.IO) {
                    ImageIO.read(pngTempFile)
                }
            bufferedImage.width shouldBe 780
            bufferedImage.height shouldBe 335
        }
    }
})