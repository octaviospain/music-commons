/******************************************************************************
 * Copyright (C) 2026  Octavio Calleya Garcia                                 *
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

package net.transgressoft.commons.media.waveform

import net.transgressoft.commons.media.util.decodeToPcmStream
import net.transgressoft.commons.music.audio.AudioFileType
import net.transgressoft.commons.music.waveform.AudioWaveform
import net.transgressoft.commons.music.waveform.AudioWaveformProcessingException
import net.transgressoft.lirp.entity.ReactiveEntityBase
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Path
import javax.imageio.ImageIO
import javax.sound.sampled.AudioInputStream
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.math.abs
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
 *
 * Decoded PCM is bounded by [maxPcmBytes] to prevent unbounded heap growth when processing
 * untrusted or decode-bomb audio files. Exceeding the ceiling throws [AudioWaveformProcessingException].
 */
@Serializable
class ScalableAudioWaveform(
    override val id: Int,
    override val audioFilePath: Path,
    val maxPcmBytes: Long = DEFAULT_MAX_PCM_BYTES
) : ReactiveEntityBase<Int, AudioWaveform>(), AudioWaveform {

    companion object {

        /**
         * Default maximum PCM bytes accumulated during waveform extraction (~1 GiB, enough for
         * roughly 1.7 hours of stereo 44.1 kHz/16-bit audio). This ceiling exists to bound
         * memory growth when decoding untrusted audio content; a crafted or infinitely-looping
         * file would otherwise exhaust the JVM heap before `getRawAudioPcm` returns.
         */
        const val DEFAULT_MAX_PCM_BYTES: Long = 1_073_741_824L
    }

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
        normalizedAmplitudes: FloatArray,
        maxPcmBytes: Long = DEFAULT_MAX_PCM_BYTES
    ) : this(id, audioFilePath, maxPcmBytes) {
        this.cachedWidth = cachedWidth
        this.normalizedAmplitudes = normalizedAmplitudes
    }

    init {
        check(audioFilePath.extension in AudioFileType.entries.map(AudioFileType::extension)) {
            "File extension '${audioFilePath.extension}' not supported"
        }
    }

    // rawAudioPcm is read and written only inside computeNormalized(), which runs exclusively while
    // holding cacheMutex (see amplitudes()). The mutex serializes all access and establishes the
    // happens-before relationship, so no @Volatile is required for cross-thread visibility.
    @Transient
    private var rawAudioPcm: IntArray? = null

    private fun getRawAudioPcm(audioFilePath: Path): IntArray {
        if (!audioFilePath.exists()) {
            throw AudioWaveformProcessingException("File '$audioFilePath' does not exist")
        }
        return try {
            val pcmStream = decodeToPcmStream(audioFilePath)
            val isBigEndian = pcmStream.format.isBigEndian
            val pcmBytes = pcmStream.use { readBoundedPcmBytes(it, audioFilePath) }
            if (pcmBytes.isEmpty()) {
                throw AudioWaveformProcessingException(
                    "No PCM data produced for '$audioFilePath' — format conversion may not be supported"
                )
            }
            decodePcmSamples(pcmBytes, isBigEndian)
        } catch (exception: AudioWaveformProcessingException) {
            throw exception
        } catch (exception: Exception) {
            throw AudioWaveformProcessingException("Error processing waveform", exception)
        }
    }

    /** Reads the full PCM stream into a byte array, failing if it exceeds [maxPcmBytes]. */
    private fun readBoundedPcmBytes(stream: AudioInputStream, audioFilePath: Path): ByteArray {
        val buf = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var accumulated = 0L
        var bytesRead = stream.read(buffer)
        while (bytesRead != -1) {
            if (bytesRead > 0) {
                accumulated += bytesRead
                if (accumulated > maxPcmBytes) {
                    throw AudioWaveformProcessingException(
                        "PCM data for '$audioFilePath' exceeds the $maxPcmBytes-byte limit"
                    )
                }
                buf.write(buffer, 0, bytesRead)
            }
            bytesRead = stream.read(buffer)
        }
        return buf.toByteArray()
    }

    /** Decodes 16-bit PCM bytes into signed sample values, respecting the stream's endianness. */
    private fun decodePcmSamples(pcmBytes: ByteArray, isBigEndian: Boolean): IntArray =
        IntArray(pcmBytes.size / 2) { i ->
            val byteOffset = i * 2
            val first = pcmBytes[byteOffset].toInt()
            val second = pcmBytes[byteOffset + 1].toInt()
            if (isBigEndian) {
                (first shl 8) or (second and 0xFF)
            } else {
                (second shl 8) or (first and 0xFF)
            }
        }

    /**
     * Computes width-normalized amplitude values from the raw PCM data, without applying
     * a height factor. Height scaling is deferred to the [amplitudes] caller,
     * allowing the same normalized array to be reused across different height requests.
     *
     * Lazily memoizes [rawAudioPcm] on first invocation to avoid redundant audio file reads.
     */
    private fun computeNormalized(width: Int): FloatArray {
        val pcm = rawAudioPcm ?: getRawAudioPcm(audioFilePath).also { rawAudioPcm = it }
        if (pcm.isEmpty()) return FloatArray(width) { 0f }
        val divisor = 32767.0f
        return FloatArray(width) { w ->
            val start = (w.toLong() * pcm.size / width).toInt()
            val endExclusive =
                (((w + 1).toLong() * pcm.size / width).toInt())
                    .coerceAtLeast(start + 1)
                    .coerceAtMost(pcm.size)
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
            ScalableAudioWaveform(id, audioFilePath, cachedWidth, cached.copyOf(), maxPcmBytes)
        } else {
            ScalableAudioWaveform(id, audioFilePath, maxPcmBytes)
        }
    }

    override fun toString() = "ScalableAudioWaveform(uniqueId=$uniqueId)"
}