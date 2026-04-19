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

import net.transgressoft.commons.music.audio.AudioFileType
import net.transgressoft.commons.music.audio.AudioFileType.FLAC
import net.transgressoft.commons.music.audio.AudioFileType.M4A
import net.transgressoft.commons.music.audio.AudioFileType.MP3
import net.transgressoft.commons.music.audio.AudioFileType.WAV
import net.transgressoft.commons.music.audio.toAudioFileType
import net.transgressoft.commons.music.common.WindowsLongPathSupport
import net.transgressoft.commons.music.common.WindowsPathValidator
import net.transgressoft.lirp.entity.ReactiveEntityBase
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
import kotlin.io.path.nameWithoutExtension
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * Implementation of [AudioWaveform] that generates scalable waveform data from audio files.
 *
 * Reads raw PCM data from audio files (transcoding non-WAV formats first) and computes
 * amplitude arrays at specified dimensions. Normalized amplitudes (without height factor)
 * are cached after the first computation so that subsequent requests for the same display
 * width are served in O(n) without re-reading the audio file. Height scaling is applied
 * at call time by multiplying cached values, allowing the same cached array to serve
 * different heights. A width change invalidates the cache and triggers full recomputation.
 *
 * The waveform data can be scaled to different widths and heights and can generate visual
 * waveform images for display purposes.
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

    @Transient private val cacheMutex = Mutex()

    @Transient internal var normalizedAmplitudes: FloatArray? = null

    internal var cachedWidth: Int = 0

    /**
     * Internal constructor for deserialization — accepts pre-computed cached state so that
     * a deserialized waveform can serve amplitude requests for the cached width without
     * reading or transcoding the audio file.
     */
    internal constructor(
        id: Int,
        audioFilePath: Path,
        cachedWidth: Int,
        normalizedAmplitudes: FloatArray
    ) : this(id, audioFilePath) {
        this.cachedWidth = cachedWidth
        this.normalizedAmplitudes = normalizedAmplitudes
    }

    private fun AudioFileType.Companion.supportedAudioTypes() =
        setOf(MP3.extension, M4A.extension, FLAC.extension, WAV.extension)

    init {
        check(audioFilePath.extension in AudioFileType.supportedAudioTypes()) {
            "File extension '${audioFilePath.extension}' not supported"
        }
    }

    @Transient private var rawAudioPcm: IntArray? = null

    private fun getRawAudioPcm(audioFilePath: Path): IntArray {
        if (!audioFilePath.exists()) {
            throw AudioWaveformProcessingException("File '$audioFilePath' does not exist")
        }
        return try {
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
        } catch (exception: AudioWaveformProcessingException) {
            throw exception
        } catch (exception: Exception) {
            throw AudioWaveformProcessingException("Error processing waveform", exception)
        }
    }

    private fun transcodeToWav(path: Path): File {
        // Strip the original extension before sanitizing so temp files are
        // "decoded_song.wav" rather than "decoded_song.mp3<rand>.wav".
        val safeName = WindowsPathValidator.sanitizeForTempFile(path.nameWithoutExtension)
        val originalExtension = path.extension
        val safePath = WindowsLongPathSupport.toLongPathSafe(path)
        val decodedFile = File.createTempFile("decoded_$safeName", "." + WAV.extension)
        val copiedFile = File.createTempFile("original_$safeName", ".$originalExtension")
        Files.copy(safePath, copiedFile.toPath(), StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING)
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
            audioPcm = IntArray(available / 2)
            AudioSystem.getAudioInputStream(decodedFormat, input).use { pcmDecodedInput ->
                val buffer = ByteArray(available)
                if (pcmDecodedInput.read(buffer, 0, available) > 0) {
                    for (i in 0 until available / 2) {
                        val byteOffset = i * 2
                        audioPcm[i] = buffer[byteOffset + 1].toInt() shl 8 or (buffer[byteOffset].toInt() and 0xff) shl 16
                        audioPcm[i] /= 32767
                    }
                }
            }
        }
        return audioPcm
    }

    /**
     * Computes width-normalized amplitude values from the raw PCM data, without applying
     * a height factor. The returned values use [amplitudeCoefficient] as the divisor exponent
     * and average samples per pixel. Height scaling is deferred to the [amplitudes] caller,
     * allowing the same normalized array to be reused across different height requests.
     *
     * Lazily memoizes [rawAudioPcm] on first invocation to avoid redundant audio file reads.
     */
    private fun computeNormalized(width: Int): FloatArray {
        val pcm = rawAudioPcm ?: getRawAudioPcm(audioFilePath).also { rawAudioPcm = it }
        val divisor = (Byte.SIZE_BITS * 2.0).pow(amplitudeCoefficient).toFloat()
        return FloatArray(width) { w ->
            val start = (w.toLong() * pcm.size / width).toInt()
            val endExclusive = (((w + 1).toLong() * pcm.size / width).toInt()).coerceAtLeast(start + 1).coerceAtMost(pcm.size)
            var amplitude = 0.0f
            for (i in start until endExclusive) {
                amplitude += abs(pcm[i]) / divisor
            }
            amplitude / (endExclusive - start).toFloat()
        }
    }

    @Throws(AudioWaveformProcessingException::class)
    override suspend fun amplitudes(width: Int, height: Int, dispatcher: CoroutineDispatcher): FloatArray {
        check(width > 0) { "Width must be greater than 0" }
        check(height > 0) { "Height must be greater than 0" }

        var computed: FloatArray? = null
        val result =
            withContext(dispatcher) {
                cacheMutex.withLock {
                    val cached = normalizedAmplitudes
                    if (cached != null && cachedWidth == width) {
                        FloatArray(cached.size) { cached[it] * height }
                    } else {
                        computeNormalized(width).also { computed = it }.let { arr ->
                            FloatArray(arr.size) { arr[it] * height }
                        }
                    }
                }
            }
        // Apply cache mutations inside mutateAndPublish so clone-compare detects the delta.
        // Fired outside the lock to avoid deadlock if a subscriber calls back into amplitudes().
        computed?.let { newAmplitudes ->
            mutateAndPublish {
                normalizedAmplitudes = newAmplitudes
                cachedWidth = width
            }
        }
        return result
    }

    override suspend fun createImage(outputFile: File, waveformColor: Color, backgroundColor: Color, width: Int, height: Int, dispatcher: CoroutineDispatcher) {
        check(width > 0) { "Width must be greater than 0" }
        check(height > 0) { "Height must be greater than 0" }

        val amplitudes = amplitudes(width, height, dispatcher)

        // The BufferedImage construction and width × height setRGB loop is non-trivial CPU work;
        // run it on the requested dispatcher together with the IO write so callers that pass
        // Dispatchers.JavaFx do not block the FX thread.
        withContext(dispatcher) {
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
            ImageIO.write(bufferedImage, "png", outputFile)
        }
    }

    /** Returns a snapshot of the cached normalized amplitudes safe for concurrent serialization. */
    internal val normalizedAmplitudesSnapshot: FloatArray?
        get() = normalizedAmplitudes?.copyOf()

    override val uniqueId: String
        get() = "$id-${audioFilePath.hashCode()}-$cachedWidth"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val that = other as ScalableAudioWaveform
        return id == that.id &&
            audioFilePath == that.audioFilePath &&
            cachedWidth == that.cachedWidth &&
            normalizedAmplitudes.contentEquals(that.normalizedAmplitudes)
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + audioFilePath.hashCode()
        result = 31 * result + cachedWidth
        result = 31 * result + normalizedAmplitudes.contentHashCode()
        return result
    }

    override fun clone(): ScalableAudioWaveform {
        val cached = normalizedAmplitudes
        return if (cached != null) {
            ScalableAudioWaveform(id, audioFilePath, cachedWidth, cached.copyOf())
        } else {
            ScalableAudioWaveform(id, audioFilePath)
        }
    }

    override fun toString() = "ScalableAudioWaveform(uniqueId=$uniqueId)"
}