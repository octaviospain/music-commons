package net.transgressoft.commons.music.waveform

import net.transgressoft.commons.music.audio.ArbitraryAudioFile.realAudioFile
import net.transgressoft.commons.music.audio.AudioFileTagType.FLAC
import net.transgressoft.commons.music.audio.AudioFileTagType.ID3_V_24
import net.transgressoft.commons.music.audio.AudioFileTagType.MP4_INFO
import net.transgressoft.commons.music.audio.AudioFileTagType.WAV
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.annotation.Tags
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.engine.spec.tempfile
import io.kotest.matchers.floats.shouldBeWithinPercentageOf
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.property.Arb
import io.kotest.property.arbitrary.next
import java.awt.Color
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
@Tags("linux-only")
internal class ScalableAudioWaveformTest : FunSpec({

    val testDispatcher = UnconfinedTestDispatcher()

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
            ScalableAudioWaveform(1, it).createImage(pngTempFile, Color.RED, Color.BLUE, 780, 335, testDispatcher)
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

    test("Throws AudioWaveformProcessingException when creating waveform from corrupted file") {
        val corruptedFile = tempfile(suffix = ".mp3")
        corruptedFile.writeText("This is not a valid mp3 file")
        val pngTempFile = tempfile(suffix = ".png")

        val exception =
            shouldThrow<AudioWaveformProcessingException> {
                ScalableAudioWaveform(1, corruptedFile.toPath()).createImage(pngTempFile, Color.RED, Color.BLUE, 780, 335, testDispatcher)
            }

        exception.message shouldContain "Error processing waveform"
        exception.cause shouldNotBe null
        exception.toString()
    }

    test("ScalableAudioWaveform amplitudes cache hit returns same-size array with linear height scaling") {
        val realAudioPath = Arb.realAudioFile(ID3_V_24).next()
        val waveform = ScalableAudioWaveform(1, realAudioPath)

        val firstResult = waveform.amplitudes(780, 335)
        val secondResult = waveform.amplitudes(780, 200)

        firstResult.size shouldBe 780
        secondResult.size shouldBe 780
        waveform.cachedWidth shouldBe 780

        // Verify linear height scaling: first[i] / second[i] ≈ 335 / 200
        val expectedRatio = 335.0f / 200.0f
        for (i in firstResult.indices) {
            if (secondResult[i] != 0.0f) {
                (firstResult[i] / secondResult[i]).shouldBeWithinPercentageOf(expectedRatio, 0.01)
            }
        }
    }

    test("ScalableAudioWaveform amplitudes width change triggers recomputation and updates cachedWidth") {
        val realAudioPath = Arb.realAudioFile(ID3_V_24).next()
        val waveform = ScalableAudioWaveform(1, realAudioPath)

        waveform.amplitudes(780, 335)
        waveform.cachedWidth shouldBe 780

        val secondResult = waveform.amplitudes(400, 335)

        secondResult.size shouldBe 400
        waveform.cachedWidth shouldBe 400
    }

    test("ScalableAudioWaveform with cached amplitudes serves data without audio file access") {
        val nonExistentPath = Path.of("/tmp/nonexistent-test-waveform-file.mp3")
        val cachedAmplitudes = FloatArray(780) { 0.5f }
        val waveform = ScalableAudioWaveform(1, nonExistentPath, 780, cachedAmplitudes)

        val result = waveform.amplitudes(780, 100)

        result.size shouldBe 780
        result.forEach { it shouldBe 50.0f }
    }

    test("ScalableAudioWaveform with cached amplitudes throws AudioWaveformProcessingException on width change when file missing") {
        val nonExistentPath = Path.of("/tmp/nonexistent-test-waveform-file.mp3")
        val cachedAmplitudes = FloatArray(780) { 0.5f }
        val waveform = ScalableAudioWaveform(1, nonExistentPath, 780, cachedAmplitudes)

        shouldThrow<AudioWaveformProcessingException> {
            waveform.amplitudes(400, 100)
        }
    }

    test("ScalableAudioWaveform amplitudes throws IllegalStateException for zero width") {
        val realAudioPath = Arb.realAudioFile(ID3_V_24).next()
        val waveform = ScalableAudioWaveform(1, realAudioPath)

        shouldThrow<IllegalStateException> {
            waveform.amplitudes(0, 100)
        }
    }

    test("ScalableAudioWaveform amplitudes throws IllegalStateException for zero height") {
        val realAudioPath = Arb.realAudioFile(ID3_V_24).next()
        val waveform = ScalableAudioWaveform(1, realAudioPath)

        shouldThrow<IllegalStateException> {
            waveform.amplitudes(780, 0)
        }
    }
})