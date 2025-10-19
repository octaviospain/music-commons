/******************************************************************************
 * Copyright (C) 2025  Octavio Calleya Garcia                                 *
 *                                                                            *
 * This program is free software: you can redistribute it and/or modify       *
 * it under the terms of the GNU General Public License as published by       *
 * the Free Software Foundation, either version 3 of the License, or          *
 * (at your option) any later version.                                        *
 *                                                                            *
 * This program is distributed in the hope that it will be useful,            *
 * but WITHOUT ANY WARRANTY; without even the implied warranty of             *
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the              *
 * GNU General Public License for more details.                               *
 *                                                                            *
 * You should have received a copy of the GNU General Public License          *
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.     *
 ******************************************************************************/

package net.transgressoft.commons.music.waveform

import net.transgressoft.commons.entity.ReactiveEntityBase
import net.transgressoft.commons.music.audio.AudioFileType
import net.transgressoft.commons.music.audio.AudioFileType.FLAC
import net.transgressoft.commons.music.audio.AudioFileType.M4A
import net.transgressoft.commons.music.audio.AudioFileType.MP3
import net.transgressoft.commons.music.audio.AudioFileType.WAV
import net.transgressoft.commons.music.audio.toAudioFileType
import ws.schild.jave.Encoder
import ws.schild.jave.MultimediaObject
import ws.schild.jave.encode.AudioAttributes
import ws.schild.jave.encode.EncodingAttributes
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import javax.imageio.ImageIO
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * Implementation of [AudioWaveform] that generates scalable waveform data from audio files.
 *
 * Reads raw PCM data from audio files (transcoding non-WAV formats first) and computes
 * amplitude arrays at specified dimensions. The waveform data can be scaled to different
 * widths and heights and can generate visual waveform images for display purposes.
 */
@Serializable
class ScalableAudioWaveform(
    override val id: Int,
    override val audioFilePath: Path
) : ReactiveEntityBase<Int, AudioWaveform>(), AudioWaveform {

    /*
        This constant is used to scale the amplitude height of the waveform
        For the waveform to be properly visible, the amplitude must be between 3.8 and 4.4
        Below 3.8, the waveform is too big and touches the limits of the canvas
        Above 4.2, the waveform can be too small and is not visible
        This anyway depends on each waveform, but this value is the one I found more balanced
     */
    @Transient private val amplitudeCoefficient = 3.9

    private fun AudioFileType.Companion.supportedAudioTypes() =
        setOf(MP3.extension, M4A.extension, FLAC.extension, WAV.extension)

    init {
        check(audioFilePath.extension in AudioFileType.supportedAudioTypes()) {
            "File extension '${audioFilePath.extension}' not supported"
        }
        check(audioFilePath.exists()) {
            "File '$audioFilePath' does not exist"
        }
    }

    @Transient private var rawAudioPcm: IntArray? = null

    private fun getRawAudioPcm(audioFilePath: Path) =
        try {
            when (audioFilePath.extension.toAudioFileType()) {
                WAV -> getRawPulseCodeModulation(audioFilePath.toFile())
                else -> {
                    transcodeToWav(audioFilePath).let { convertedFile ->
                        getRawPulseCodeModulation(convertedFile).also {
                            Files.delete(convertedFile.toPath())
                        }
                    }
                }
            }
        } catch (exception: Exception) {
            throw AudioWaveformProcessingException("Error processing waveform", exception)
        }

    private fun transcodeToWav(path: Path): File {
        val fileName = path.fileName.toString()
        val decodedFile = File.createTempFile("decoded_$fileName", "." + WAV.extension)
        val copiedFile = File.createTempFile("original_$fileName", "." + path.extension)
        Files.copy(path, copiedFile.toPath(), StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING)
        try {
            Encoder().encode(
                MultimediaObject(copiedFile), decodedFile,
                EncodingAttributes().apply {
                    setOutputFormat(WAV.extension)
                    setAudioAttributes(
                        AudioAttributes().apply {
                            setCodec("pcm_s16le")
                            setBitRate(16000)
                            setChannels(2)
                            setSamplingRate(44100)
                        }
                    )
                }
            )
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

    @Throws(AudioWaveformProcessingException::class)
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

    @Throws(AudioWaveformProcessingException::class)
    private fun getScaledPulseCodeModulation(height: Int): IntArray =
        rawAudioPcm ?: getRawAudioPcm(audioFilePath).let {
            IntArray(it.size).also { scaledRawPcm ->
                for (i in it.indices) {
                    scaledRawPcm[i] = it[i] * height
                }
            }
        }

    override suspend fun createImage(outputFile: File, waveformColor: Color, backgroundColor: Color, width: Int, height: Int) {
        check(width > 0) { "Width must be greater than 0" }
        check(height > 0) { "Height must be greater than 0" }

        val amplitudes = amplitudes(width, height)

        val bufferedImage =
            BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB).apply {
                for (x in 0 until width) {
                    for (y in 0 until height) {
                        val absoluteAmplitude = amplitudes[x].roundToInt()
                        val y1: Int = (height - 2 * absoluteAmplitude) / 2
                        val y2: Int = y1 + 2 * absoluteAmplitude

                        if (y in y1..y2) {
                            setRGB(x, y, waveformColor.rgb)
                        } else {
                            setRGB(x, y, backgroundColor.rgb)
                        }
                    }
                }
            }

        withContext(Dispatchers.IO) {
            ImageIO.write(bufferedImage, "png", outputFile)
        }
    }

    override val uniqueId: String
        get() = "$id-${rawAudioPcm.contentHashCode()}"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val that = other as ScalableAudioWaveform
        return rawAudioPcm.contentEquals(that.rawAudioPcm)
    }

    override fun hashCode() = rawAudioPcm.contentHashCode()

    override fun clone(): ScalableAudioWaveform = ScalableAudioWaveform(id, audioFilePath)

    override fun toString() = "ScalableAudioWaveform(uniqueId=$uniqueId)"
}