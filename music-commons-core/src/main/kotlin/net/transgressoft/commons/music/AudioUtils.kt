package net.transgressoft.commons.music

import be.tarsos.transcoder.DefaultAttributes
import be.tarsos.transcoder.Transcoder
import be.tarsos.transcoder.ffmpeg.EncoderException
import net.transgressoft.commons.music.waveform.AudioWaveformProcessingException
import org.jetbrains.kotlin.com.google.common.base.CharMatcher
import org.jetbrains.kotlin.com.google.common.base.Splitter
import java.awt.Color
import java.awt.image.BufferedImage
import java.awt.image.BufferedImage.TYPE_INT_RGB
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.regex.Pattern
import javax.imageio.ImageIO
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.UnsupportedAudioFileException
import kotlin.io.path.extension
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

object AudioUtils {

    /**********************************************************************************
     *          Functions to extract PCM amplitudes from an audio file
     **********************************************************************************/

    fun extractWaveformToImage(audioFilePath: Path, outputFile: File, width: Int, height: Int, color: Color = Color.RED) {
        val amplitudes = extractWaveformAmplitudes(audioFilePath, width, height)

        val bufferedImage = BufferedImage(width, height, TYPE_INT_RGB).apply {
            for (x in 0 until width) {
                val absoluteAmplitude = amplitudes[x].roundToInt()
                val y1: Int = (height - 2 * absoluteAmplitude) / 2
                val y2: Int = y1 + 2 * absoluteAmplitude
                for (y in y1..y2) {
                    setRGB(x, y, color.rgb)
                }
            }
        }

        ImageIO.write(bufferedImage, "png", outputFile)
    }

    @Throws(AudioWaveformProcessingException::class)
    fun extractWaveformAmplitudes(audioFilePath: Path, width: Int, height: Int): FloatArray {
        if (!audioFilePath.toFile().exists()) throw AudioWaveformProcessingException("File does not exist $audioFilePath")
        val extension = audioFilePath.extension
        return try {
            when (extension) {
                "wav" -> processWavFile(audioFilePath, width, height)
                "mp3", "m4a" -> processNonWavFile(audioFilePath, width, height)
                else -> throw AudioWaveformProcessingException("File extension '$extension' not supported")
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
        val divisor = (Byte.SIZE_BITS * 2.0).pow(4.0).toFloat() // meant to be 65536.0f
        for (w in 0 until width) {
            var amplitude = 0.0f
            for (s in 0 until samplesPerPixel) {
                amplitude += abs(audioPcm[w * samplesPerPixel + s]) / divisor
            }
            amplitude /= samplesPerPixel.toFloat()
            waveformAmplitudes[w] = amplitude
        }
        return waveformAmplitudes
    }

    /**********************************************************************************
     *  Function to get artist names in the title, artist field and album artist field
     **********************************************************************************/

    private val endsWithRemix = Pattern.compile("[(|\\[](\\s*(&?\\s*(\\w+)\\s+)+(?i)(remix))[)|\\]]")
    private val startsWithRemixBy = Pattern.compile("[(|\\[](?i)(remix)(\\s+)(?i)(by)(.+)[)|\\]]")
    private val hasFt = Pattern.compile("[(\\[|\\s](?i)(ft) (.+)")
    private val hasFeat = Pattern.compile("[(\\[|\\s](?i)(feat) (.+)")
    private val hasFeaturing = Pattern.compile("[(\\[|\\s](?i)(featuring) (.+)")
    private val startsWithWith = Pattern.compile("[(|\\[](?i)(with) (.+)[)|\\]]")

    private val artistsRegexMap: Map<Pattern, Pattern> = buildMap {
        set(Pattern.compile(" (?i)(remix)"), endsWithRemix)
        set(Pattern.compile("(?i)(remix)(\\s+)(?i)(by) "), startsWithRemixBy)
        set(Pattern.compile("(?i)(ft) "), hasFt)
        set(Pattern.compile("(?i)(feat) "), hasFeat)
        set(Pattern.compile("(?i)(featuring) "), hasFeaturing)
        set(Pattern.compile("(?i)(with) "), startsWithWith)
    }

    /**
     * Returns the names of the artists that are involved in the fields of an [AudioItem],
     * that is, every artist that could appear in the [AudioItem.artist] variable,
     * or [Album.albumArtist] or in the [AudioItem.title].
     *
     * <h2>Example</h2>
     *
     *
     * The following AudioItem instance:
     *
     * audioItem.name = "Who Controls (Adam Beyer Remix)"
     * audioItem.artist = "David Meiser, Black Asteroid & Tiga"
     * audioItem.albumArtist = "Ida Engberg"
     *
     * ... produces the following (without order):
     *
     * `[David Meiser, Black Asteroid, Tiga, Adam Beyer, Ida Engberg]
     *
     * @param title           The title of an audio item
     * @param artistName      The artist name of an audio item
     * @param albumArtistName The album artist name of an audio item
     *
     * @return An `ImmutableSet` object with the names of the artists
     */
    fun getArtistsNamesInvolved(title: String, artistName: String, albumArtistName: String): Set<String> {
        val artistsInvolved: MutableSet<String> = mutableSetOf()
        val albumArtistNames = Splitter.on(CharMatcher.anyOf(",&"))
            .trimResults()
            .omitEmptyStrings()
            .splitToList(albumArtistName)

        artistsInvolved.addAll(albumArtistNames)
        artistsInvolved.addAll(getNamesInArtist(artistName))
        artistsInvolved.addAll(getNamesInTitle(title))
        artistsInvolved.remove("")
        return artistsInvolved
    }

    /**
     * Returns artist names that are in the given artist name.
     * Commonly they can be separated by ',' or '&' characters, or by the words 'versus' or 'vs'.
     *
     * <h3>Example</h3>
     *
     *
     * The given audio item artist field:
     *
     * "David Meiser, Black Asteroid & Tiga"
     *
     * ... produces the following set (without order):
     *
     * [David Meiser, Black Asteroid, Tiga]
     *
     * @param artistName The artist name from where to find more names
     *
     * @return A Set with the artists found
     */
    private fun getNamesInArtist(artistName: String): Set<String> =
        artistName.split("((\\s+(?i)(versus)\\s+)|(\\s+(?i)(vs)(\\.|\\s+))|(\\s+(?i)(feat)(\\.|\\s+))|(\\s+(?i)(ft)(\\.|\\s+))|(\\s*,\\s*)|(\\s+&\\s+))".toRegex())
            .map { it.trim().replaceFirstChar(Char::titlecase) }
            .map { it.split(" ").joinToString(" ") { itt -> itt.replaceFirstChar(Char::titlecase) } }
            .map { beautifyArtistName(it) }
            .toSet()

    /**
     * Returns the names of the artists that are in a given string which is the title of an [AudioItem].
     * For example:
     *
     * The following audio item name field:
     *
     * Song name (Adam Beyer & Pete Tong Remix)
     *
     * ... produces the following (without order):
     *
     * [Adam Beyer, Pete Tong]
     *
     * @param title The `String` where to find artist names
     *
     * @return A Set with the artists found
     */
    private fun getNamesInTitle(title: String): Set<String> {
        val artistsInsideParenthesis = mutableSetOf<String>()
        for ((keyPattern, value) in artistsRegexMap) {
            val matcher = value.matcher(title)
            if (matcher.find()) {
                val insideParenthesisString = title.substring(matcher.start())
                    .replace("[(\\[|)\\]]".toRegex(), "")
                    .replace(keyPattern.pattern().toRegex(), "")
                    .replace("\\s(?i)(vs)\\s".toRegex(), "&")
                    .replace("\\s+".toRegex(), " ")

                artistsInsideParenthesis.addAll(
                    Splitter.on(CharMatcher.anyOf("&,"))
                        .trimResults()
                        .omitEmptyStrings()
                        .splitToList(insideParenthesisString)
                )
                break
            }
        }
        return artistsInsideParenthesis
            .map { it.split(" ").joinToString(" ") { itt -> itt.replaceFirstChar(Char::titlecase) } }
            .toSet()
    }

    fun beautifyArtistName(name: String): String {
        return name.replaceFirstChar(Char::titlecase)
            .replace("\\s+".toRegex(), " ")
            .replace(" (?i)(vs)(\\.|\\s)".toRegex(), " vs ")
            .replace(" (?i)(versus) ".toRegex(), " versus ")
    }
}