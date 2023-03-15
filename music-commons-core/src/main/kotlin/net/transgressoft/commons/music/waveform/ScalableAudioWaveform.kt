package net.transgressoft.commons.music.waveform

import javafx.scene.paint.Color
import ws.schild.jave.Encoder
import ws.schild.jave.MultimediaObject
import ws.schild.jave.encode.AudioAttributes
import ws.schild.jave.encode.EncodingAttributes
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.*
import javax.imageio.ImageIO
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

class ScalableAudioWaveform(
    override val id: Int,
    audioFilePath: Path,
) : AudioWaveform {

    /*
        This constant is used to scale the amplitude height of the waveform
        For the waveform to be properly visible, the amplitude must be between 3.8 and 4.4
        Below 3.8, the waveform is too big and touches the limits of the canvas
        Above 4.2, the waveform can be too small and is not visible
        This anyway depends on each waveform, but this value is the one I found more balanced
    */
    private val amplitudeCoefficient = 3.9

    private val rawAudioPcm: IntArray

    init {
        check(audioFilePath.exists()) {
            "File '${audioFilePath}' does not exist"
        }

        rawAudioPcm = getRawAudioPcm(audioFilePath)
    }

    private fun getRawAudioPcm(audioFilePath: Path) = try {
        when (audioFilePath.extension) {
            "wav" -> getRawPulseCodeModulation(audioFilePath.toFile())
            "mp3", "m4a", "flac" -> {
                transcodeToWav(audioFilePath).let { convertedFile ->
                    getRawPulseCodeModulation(convertedFile).also {
                        Files.delete(convertedFile.toPath())
                    }
                }
            }

            else -> throw AudioWaveformProcessingException("File extension '${audioFilePath.extension}' not supported")
        }
    } catch (exception: Exception) {
        throw AudioWaveformProcessingException("Error processing waveform", exception)
    }

    private fun transcodeToWav(path: Path): File {
        val fileName = path.fileName.toString()
        val decodedFile = File.createTempFile("decoded_$fileName", ".wav")
        val copiedFile = File.createTempFile("original_$fileName", ".mp3")
        Files.copy(path, copiedFile.toPath(), StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING)
        try {
            Encoder().encode(MultimediaObject(copiedFile), decodedFile, EncodingAttributes().apply {
                setOutputFormat("wav")
                setAudioAttributes(AudioAttributes().apply {
                    setCodec("pcm_s16le")
                    setBitRate(16)
                    setChannels(2)
                    setSamplingRate(44100)
                })
            })
        } finally {
            Files.delete(copiedFile.toPath())
        }
        return decodedFile
    }

    private fun getRawPulseCodeModulation(file: File): IntArray {
        var audioPcm: IntArray
        AudioSystem.getAudioInputStream(file).use { input ->
            val baseFormat = input.format
            val encoding = AudioFormat.Encoding.PCM_UNSIGNED
            val sampleRate = baseFormat.sampleRate
            val sampleSizeInBits = 16
            val numChannels = baseFormat.channels
            val frameSize = numChannels * 2
            val decodedFormat = AudioFormat(encoding, sampleRate, sampleSizeInBits, numChannels, frameSize, sampleRate, false)
            val available = input.available()
            audioPcm = IntArray(available)
            AudioSystem.getAudioInputStream(decodedFormat, input).use { pcmDecodedInput ->
                val buffer = ByteArray(available)
                if (pcmDecodedInput.read(buffer, 0, available) > 0) {
                    for (i in 0 until available - 1 step 2) {
                        audioPcm[i] = buffer[i + 1].toInt() shl 8 or (buffer[i].toInt() and 0xff) shl 16
                        audioPcm[i] /= 32767
                    }
                }
            }
        }
        return audioPcm
    }

    override suspend fun amplitudes(width: Int, height: Int): FloatArray {
        check(width > 0) { "Width must be greater than 0" }
        check(height > 0) { "Height must be greater than 0" }

        val scaledAudioPcm = getScaledPulseCodeModulation(height)

        val waveformAmplitudes = FloatArray(width)
        val samplesPerPixel = scaledAudioPcm.size / width
        val divisor = (Byte.SIZE_BITS * 2.0).pow(amplitudeCoefficient).toFloat()
        for (w in 0 until width) {
            var amplitude = 0.0f
            val samplesAtWidth = w * samplesPerPixel
            for (s in 0 until samplesPerPixel) {
                amplitude += abs(scaledAudioPcm[samplesAtWidth + s]) / divisor
            }
            amplitude /= samplesPerPixel.toFloat()
            waveformAmplitudes[w] = amplitude
        }
        return waveformAmplitudes
    }

    private fun getScaledPulseCodeModulation(height: Int) = IntArray(rawAudioPcm.size).also { scaledRawPcm ->
        for (i in rawAudioPcm.indices) {
            scaledRawPcm[i] = rawAudioPcm[i] * height
        }
    }

    override suspend fun createImage(outputFile: File, waveformColor: Color, backgroundColor: Color, width: Int, height: Int) {
        check(width > 0) { "Width must be greater than 0" }
        check(height > 0) { "Height must be greater than 0" }

        val amplitudes = amplitudes(width, height)
        val backgroundRgb = java.awt.Color(
            backgroundColor.red.toFloat(),
            backgroundColor.green.toFloat(),
            backgroundColor.blue.toFloat(),
            backgroundColor.opacity.toFloat()
        ).rgb

        val waveformRgb = java.awt.Color(
            waveformColor.red.toFloat(),
            waveformColor.green.toFloat(),
            waveformColor.blue.toFloat(),
            waveformColor.opacity.toFloat()
        ).rgb

        val bufferedImage = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB).apply {
            for (x in 0 until width) {
                for (y in 0 until height) {
                    val absoluteAmplitude = amplitudes[x].roundToInt()
                    val y1: Int = (height - 2 * absoluteAmplitude) / 2
                    val y2: Int = y1 + 2 * absoluteAmplitude

                    if (y in y1..y2) {
                        setRGB(x, y, waveformRgb)
                    } else {
                        setRGB(x, y, backgroundRgb)
                    }
                }
            }
        }

        ImageIO.write(bufferedImage, "png", outputFile)
    }

    override val uniqueId: String
        get() {
            val joiner = StringJoiner("-")
            joiner.add(id.toString())
            joiner.add(rawAudioPcm.contentHashCode().toString())
            return joiner.toString()
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val that = other as ScalableAudioWaveform
        return com.google.common.base.Objects.equal(rawAudioPcm, that.rawAudioPcm)
    }

    override fun hashCode(): Int {
        return com.google.common.base.Objects.hashCode(rawAudioPcm)
    }
}