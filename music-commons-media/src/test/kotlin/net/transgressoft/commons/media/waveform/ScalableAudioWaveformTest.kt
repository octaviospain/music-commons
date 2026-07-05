package net.transgressoft.commons.media.waveform

import net.transgressoft.commons.media.player.SUPPORTED_FORMATS
import net.transgressoft.commons.music.audio.ArbitraryAudioFile.realAudioFile
import net.transgressoft.commons.music.audio.AudioFileTagType.WAV
import net.transgressoft.commons.music.waveform.AudioWaveformProcessingException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.floats.shouldBeWithinPercentageOf
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.property.Arb
import io.kotest.property.arbitrary.next
import java.awt.Color
import java.io.File
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
internal class ScalableAudioWaveformTest : FunSpec({

    val testDispatcher = UnconfinedTestDispatcher()

    context("Creates a waveform image from") {
        withData(SUPPORTED_FORMATS.mapValues { Arb.realAudioFile(it.value).next() }) {
            val pngTempFile = deleteOnExitTempFile(".png")
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
        val corruptedFile = deleteOnExitTempFile(".mp3")
        corruptedFile.writeText("This is not a valid mp3 file")
        val pngTempFile = deleteOnExitTempFile(".png")

        val exception =
            shouldThrow<AudioWaveformProcessingException> {
                ScalableAudioWaveform(1, corruptedFile.toPath()).createImage(pngTempFile, Color.RED, Color.BLUE, 780, 335, testDispatcher)
            }

        exception.message shouldContain "Error processing waveform"
        exception.cause shouldNotBe null
    }

    test("ScalableAudioWaveform amplitudes cache hit returns same-size array with linear height scaling") {
        val realAudioPath = Arb.realAudioFile(WAV).next()
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
        val realAudioPath = Arb.realAudioFile(WAV).next()
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

    context("ScalableAudioWaveform amplitudes throws IllegalStateException for non-positive dimensions") {
        data class DimensionCase(val width: Int, val height: Int)
        withData(
            nameFn = { "width=${it.width}, height=${it.height}" },
            DimensionCase(width = 0, height = 100),
            DimensionCase(width = 780, height = 0)
        ) { (width, height) ->
            val waveform = ScalableAudioWaveform(1, Arb.realAudioFile(WAV).next())

            shouldThrow<IllegalStateException> {
                waveform.amplitudes(width, height)
            }
        }
    }
})

// Kotest's tempfile() deletes registered files in afterSpec; on Windows an audio/image decoder
// that still holds the handle makes that deletion throw TempFileDeletionException and fails the
// whole spec. deleteOnExit defers cleanup to JVM shutdown, after the handles are released.
private fun deleteOnExitTempFile(suffix: String): File =
    File.createTempFile("waveform-test-", suffix).apply { deleteOnExit() }