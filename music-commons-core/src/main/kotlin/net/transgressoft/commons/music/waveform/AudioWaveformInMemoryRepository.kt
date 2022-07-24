package net.transgressoft.commons.music.waveform

import be.tarsos.transcoder.DefaultAttributes
import be.tarsos.transcoder.Transcoder
import be.tarsos.transcoder.ffmpeg.EncoderException
import net.transgressoft.commons.music.audio.AudioItem
import net.transgressoft.commons.query.EntityAttribute
import net.transgressoft.commons.query.InMemoryRepository
import org.apache.commons.io.FilenameUtils
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.*
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.UnsupportedAudioFileException

internal class ImmutableAudioWaveform (
    override val id: Int,
    override val amplitudes: FloatArray,
    override val width: Int,
    override val height: Int
) : AudioWaveform {

    private val attributeMap: Map<EntityAttribute<*>, Any> = mapOf()

    override val uniqueId: String
        get() {
            val joiner = StringJoiner("-")
            joiner.add(id.toString())
            joiner.add(width.toString())
            joiner.add(height.toString())
            joiner.add(amplitudes.size.toString())
            return joiner.toString()
        }

    override fun <A : EntityAttribute<V>, V> getAttribute(attribute: A): V {
        return attributeMap[attribute] as V
    }

    override fun scale(width: Int, height: Int): AudioWaveform {
        throw UnsupportedOperationException("Not implemented")
        // TODO Do some math and figure out how to scale the amplitudes given the new width and height without processing again
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val that = other as ImmutableAudioWaveform
        return width == that.width && height == that.height &&
                com.google.common.base.Objects.equal(amplitudes, that.amplitudes)
    }

    override fun hashCode(): Int {
        return com.google.common.base.Objects.hashCode(width, height, amplitudes)
    }
}

class AudioWaveformInMemoryRepository<W : AudioWaveform>(entitiesById: MutableMap<Int, W>) : InMemoryRepository<W>(entitiesById, null),
    AudioWaveformRepository<W> {

    companion object {
        private val emptyWaveforms: MutableMap<Pair<Short, Short>, Any> = mutableMapOf()

        fun <X : AudioWaveform> emptyWaveform(width: Short, height: Short): X {
            val dimensionPair = Pair(width, height)
            val fakeAmplitudes = FloatArray(width.toInt())
            Arrays.fill(fakeAmplitudes, 0.0f)

            return if (emptyWaveforms.contains(dimensionPair))
                emptyWaveforms[dimensionPair] as X
            else
                ImmutableAudioWaveform(-1, fakeAmplitudes, width.toInt(), height.toInt()) as X
        }
    }

    constructor() : this(mutableMapOf())

    @Throws(AudioWaveformProcessingException::class)
    override fun create(audioItem: AudioItem, width: Short, height: Short): W {
        Objects.requireNonNull(audioItem)
        val amplitudes = AudioWaveformExtractor().extractWaveform(audioItem.path(), width.toInt(), height.toInt())
        val waveform = ImmutableAudioWaveform(audioItem.id, amplitudes, width.toInt(), height.toInt()) as W
        add(waveform)
        return waveform
    }
}

internal class AudioWaveformExtractor {

    @Throws(AudioWaveformProcessingException::class)
    fun extractWaveform(path: Path, width: Int, height: Int): FloatArray {
        if (!path.toFile().exists()) throw AudioWaveformProcessingException("File does not exist $path")
        val extension = FilenameUtils.getExtension(path.fileName.toString())
        return try {
            when (extension) {
                "wav" -> processWavFile(path, width, height)
                "mp3", "m4a" -> processNonWavFile(path, width, height)
                else -> throw AudioWaveformProcessingException("File extension " + extension + "not supported")
            }
        } catch (exception: UnsupportedAudioFileException) {
            throw AudioWaveformProcessingException("Error processing waveform", exception)
        } catch (exception: IOException) {
            throw AudioWaveformProcessingException("Error processing waveform", exception)
        } catch (exception: EncoderException) {
            throw AudioWaveformProcessingException("Error processing waveform", exception)
        }
    }

    @Throws(IOException::class, UnsupportedAudioFileException::class)
    private fun processWavFile(path: Path, width: Int, height: Int): FloatArray {
        val audioPcm = getPulseCodeModulation(path.toFile(), height)
        return getWaveformAmplitudes(audioPcm, width)
    }

    @Throws(IOException::class, EncoderException::class, UnsupportedAudioFileException::class)
    private fun processNonWavFile(path: Path, width: Int, height: Int): FloatArray {
        val transcodedAudioFile = transcodeToWav(path)
        val audioWaveform = processWavFile(transcodedAudioFile.toPath(), width, height)
        Files.delete(transcodedAudioFile.toPath())
        return audioWaveform
    }

    @Throws(EncoderException::class, IOException::class)
    private fun transcodeToWav(path: Path): File {
        val fileName = path.fileName.toString()
        val decodedFile = File.createTempFile("decoded_$fileName", ".wav")
        val copiedFile = File.createTempFile("original_$fileName", null)
        Files.copy(path, copiedFile.toPath(), StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING)
        val attributes = DefaultAttributes.WAV_PCM_S16LE_STEREO_44KHZ.attributes
        try {
            Transcoder.transcode(copiedFile.toString(), decodedFile.toString(), attributes)
        } catch (exception: EncoderException) {
            if (!exception.message!!.startsWith("Source and target should")) {
                // even with this error message the library does the conversion, who knows why
                Files.delete(decodedFile.toPath())
                Files.delete(copiedFile.toPath())
                throw exception
            }
        }
        Files.delete(copiedFile.toPath())
        return decodedFile
    }

    @Throws(UnsupportedAudioFileException::class, IOException::class)
    private fun getPulseCodeModulation(file: File, height: Int): IntArray {
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
                    var i = 0
                    while (i < available - 1) {
                        audioPcm[i] = buffer[i + 1].toInt() shl 8 or (buffer[i].toInt() and 0xff) shl 16
                        audioPcm[i] /= 32767
                        audioPcm[i] *= height
                        i += 2
                    }
                }
            }
        }
        return audioPcm
    }

    private fun getWaveformAmplitudes(audioPcm: IntArray, width: Int): FloatArray {
        val waveformAmplitudes = FloatArray(width)
        val samplesPerPixel = audioPcm.size / width
        val divisor = Math.pow(java.lang.Byte.SIZE * 2.0, 4.0).toFloat() // meant to be 65536.0f
        for (w in 0 until width) {
            var amplitude = 0.0f
            for (s in 0 until samplesPerPixel) {
                amplitude += Math.abs(audioPcm[w * samplesPerPixel + s]) / divisor
            }
            amplitude /= samplesPerPixel.toFloat()
            waveformAmplitudes[w] = amplitude
        }
        return waveformAmplitudes
    }
}