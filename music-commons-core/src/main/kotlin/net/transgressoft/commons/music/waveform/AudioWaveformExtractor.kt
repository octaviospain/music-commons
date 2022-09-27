package net.transgressoft.commons.music.waveform

import be.tarsos.transcoder.DefaultAttributes
import be.tarsos.transcoder.Transcoder
import be.tarsos.transcoder.ffmpeg.EncoderException
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.UnsupportedAudioFileException
import kotlin.io.path.extension

internal class AudioWaveformExtractor {

    @Throws(AudioWaveformProcessingException::class)
    fun extractWaveform(path: Path, width: Int, height: Int): FloatArray {
        if (!path.toFile().exists()) throw AudioWaveformProcessingException("File does not exist $path")
        val extension = path.extension
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

    private fun processWavFile(path: Path, width: Int, height: Int): FloatArray {
        val audioPcm = getPulseCodeModulation(path.toFile(), height)
        return getWaveformAmplitudes(audioPcm, width)
    }

    private fun processNonWavFile(path: Path, width: Int, height: Int): FloatArray {
        val transcodedAudioFile = transcodeToWav(path)
        val audioWaveform = processWavFile(transcodedAudioFile.toPath(), width, height)
        Files.delete(transcodedAudioFile.toPath())
        return audioWaveform
    }

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