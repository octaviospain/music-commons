package net.transgressoft.commons.music.waveform

import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.engine.spec.tempfile
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import javafx.scene.paint.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.transgressoft.commons.music.audio.AudioItemTestUtil2.flacFile
import net.transgressoft.commons.music.audio.AudioItemTestUtil2.m4aFile
import net.transgressoft.commons.music.audio.AudioItemTestUtil2.mp3File
import net.transgressoft.commons.music.audio.AudioItemTestUtil2.wavFile
import java.io.File
import javax.imageio.ImageIO

class ScalableAudioWaveformTest : FunSpec({

    data class AudioFile(val audioFile: File)

    context("Create waveform image from") {
        withData(
            mapOf(
                "a wav file" to AudioFile(wavFile),
                "a mp3 file" to AudioFile(mp3File),
                "a m4aFile" to AudioFile(m4aFile),
                "a flac file" to AudioFile(flacFile)
            )
        ) {
            val pngTempFile = tempfile(suffix = ".png")
            ScalableAudioWaveform(1, it.audioFile.toPath()).createImage(pngTempFile, Color.RED, Color.BLUE, 780, 335)
            pngTempFile.exists() shouldBe true

            pngTempFile.extension shouldBe "png"
            pngTempFile.length() shouldNotBe null

            val bufferedImage = withContext(Dispatchers.IO) {
                ImageIO.read(pngTempFile)
            }
            bufferedImage.width shouldBe 780
            bufferedImage.height shouldBe 335
        }
    }
})
