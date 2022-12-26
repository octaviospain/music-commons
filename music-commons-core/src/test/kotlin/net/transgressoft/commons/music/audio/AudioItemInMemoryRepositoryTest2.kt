package net.transgressoft.commons.music.audio

import com.neovisionaries.i18n.CountryCode
import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.engine.spec.tempfile
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.transgressoft.commons.music.audio.AudioItemUtils.beautifyArtistName
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.audio.wav.WavOptions
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.TagOptionSingleton
import org.jaudiotagger.tag.flac.FlacTag
import org.jaudiotagger.tag.id3.ID3v24Tag
import org.jaudiotagger.tag.images.ArtworkFactory
import org.jaudiotagger.tag.mp4.Mp4Tag
import org.jaudiotagger.tag.wav.WavInfoTag
import org.jaudiotagger.tag.wav.WavTag
import java.awt.image.BufferedImage
import java.awt.image.DataBufferByte
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.time.Duration
import javax.imageio.ImageIO
import kotlin.io.path.absolutePathString


internal class AudioItemInMemoryRepositoryTest2 : BehaviorSpec({

    fun String.asURI(): URI = object {}.javaClass.getResource(this)!!.toURI()

    val mp3File = File("/testfiles/testeable.mp3".asURI())
    val m4aFile = File("/testfiles/testeable.m4a".asURI())
    val flacFile = File("/testfiles/testeable.flac".asURI())
    val wavFile = File("/testfiles/testeable.wav".asURI())
    val testCover = File("/testfiles/cover.jpg".asURI())
    val testCover2 = File("/testfiles/cover-2.jpg".asURI())
    val coverBytes = Files.readAllBytes(testCover.toPath())

    val arbitraryMp3Tag = arbitrary { setRandomAudioItemTagFields(ID3v24Tag(), coverBytes) }
    val arbitraryM4aTag = arbitrary { setRandomAudioItemTagFields(Mp4Tag(), coverBytes) }
    val arbitraryFlacTag = arbitrary { setRandomAudioItemTagFields(FlacTag(), coverBytes) }
    val arbitraryWavTag = arbitrary {
        val wavTag = WavTag(WavOptions.READ_ID3_ONLY).apply {
            iD3Tag = ID3v24Tag()
            infoTag = WavInfoTag()
        }
        setRandomAudioItemTagFields(wavTag, coverBytes)
    }

    val arbitraryAudioItemChange = arbitrary {
        AudioItemMetadataChange(
            title = Arb.string().bind(),
            artist = ImmutableArtist(beautifyArtistName(Arb.string().bind()), CountryCode.values().random()),
            albumName = Arb.string().bind(),
            albumArtist = ImmutableArtist(beautifyArtistName(Arb.string().bind())),
            isCompilation = Arb.boolean().bind(),
            year = Arb.short().bind(),
            label = ImmutableLabel(Arb.string().bind()),
            coverImage = Files.readAllBytes(testCover2.toPath()),
            genre = Genre.values().random(),
            comments = Arb.string().bind(),
            trackNumber = Arb.short().bind(),
            discNumber = Arb.short().bind(),
            bpm = Arb.positiveInt(230).bind().toFloat()
        )
    }

    lateinit var audioRepository: AudioItemRepository<AudioItem>

    beforeContainer {
        audioRepository = AudioItemInMemoryRepository()
    }

    given("An AudioItemRepository") {

        and("A mp3 file") {
            checkAll(20, arbitraryMp3Tag, arbitraryAudioItemChange) { mp3Tag, audioItemChange ->

                val mp3TempFile = tempfile(suffix = ".mp3").also {
                    withContext(Dispatchers.IO) {
                        Files.copy(mp3File.toPath(), it.toPath(), StandardCopyOption.REPLACE_EXISTING)
                        AudioFileIO.read(it).apply {
                            tag = mp3Tag
                            commit()
                        }
                    }
                }

                When("An AudioItem is created") {
                    val audioItem = audioRepository.createFromFile(mp3TempFile.toPath())
                    audioRepository.shouldContainExactly(audioItem)

                    then("its properties should match the metadata of the file") {
                        assertSoftly {
                            audioItem.id shouldNotBe null
                            audioItem.encoding shouldBe "MPEG-1 Layer 3"
                            audioItem.bitRate shouldBe 143
                            audioItem.duration shouldBe Duration.ofSeconds(8)
                        }
                        assertAudioItem(audioItem, mp3TempFile.toPath(), mp3Tag)
                    }

                    then("its properties are modified when the audioItem is modified") {
                        audioRepository.editAudioItemMetadata(audioItem, audioItemChange)
                        val updatedAudioItem = audioRepository.findById(audioItem.id).get()

                        updatedAudioItem shouldNotBe audioItem
                        updatedAudioItem.id shouldBe audioItem.id
                        assertAudioItemChange(updatedAudioItem, audioItemChange, ID3v24Tag().apply {
                            setField(FieldKey.ENCODER, audioItem.encoder)
                        })
                    }
                }
            }
        }

        and("A m4a file") {
            checkAll(20, arbitraryM4aTag, arbitraryAudioItemChange) { m4aTag, audioItemChange ->

                val m4aTempFile = tempfile(suffix = ".m4a").also {
                    withContext(Dispatchers.IO) {
                        Files.copy(m4aFile.toPath(), it.toPath(), StandardCopyOption.REPLACE_EXISTING)
                        AudioFileIO.read(it).apply {
                            tag = m4aTag
                            commit()
                        }
                    }
                }

                When("An AudioItem is created") {
                    val audioItem = audioRepository.createFromFile(m4aTempFile.toPath())
                    audioRepository.shouldContainExactly(audioItem)

                    then("its properties should match the metadata of the file") {
                        assertSoftly {
                            audioItem.id shouldNotBe null
                            audioItem.encoding shouldBe "Aac"
                            audioItem.bitRate shouldBe 256
                            audioItem.duration shouldBe Duration.ofSeconds(42)
                        }
                        assertAudioItem(audioItem, m4aTempFile.toPath(), m4aTag)
                    }

                    then("its properties are modified when the audioItem is modified") {
                        audioRepository.editAudioItemMetadata(audioItem, audioItemChange)
                        val updatedAudioItem = audioRepository.findById(audioItem.id).get()

                        updatedAudioItem shouldNotBe audioItem
                        updatedAudioItem.id shouldBe audioItem.id
                        assertAudioItemChange(updatedAudioItem, audioItemChange, Mp4Tag().apply {
                            setField(FieldKey.ENCODER, audioItem.encoder)
                        })
                    }
                }
            }
        }

        and("a flac file") {
            checkAll(20, arbitraryFlacTag, arbitraryAudioItemChange) { flacTag, audioItemChange ->

                val flacTempFile = tempfile(suffix = ".flac").also {
                    withContext(Dispatchers.IO) {
                        Files.copy(flacFile.toPath(), it.toPath(), StandardCopyOption.REPLACE_EXISTING)
                        AudioFileIO.read(it).apply {
                            tag = flacTag
                            commit()
                        }
                    }
                }

                When("An AudioItem is created") {
                    val audioItem = audioRepository.createFromFile(flacTempFile.toPath())
                    audioRepository.shouldContainExactly(audioItem)

                    then("its properties should match the metadata of the file") {
                        assertAudioItem(audioItem, flacTempFile.toPath(), flacTag)
                        assertSoftly {
                            audioItem.id shouldNotBe null
                            audioItem.encoding shouldBe "FLAC 16 bits"
                            audioItem.bitRate shouldBe 445
                            audioItem.duration shouldBe Duration.ofSeconds(28)
                        }
                    }

                    then("its properties are modified when the audioItem is modified") {
                        audioRepository.editAudioItemMetadata(audioItem, audioItemChange)
                        val updatedAudioItem = audioRepository.findById(audioItem.id).get()

                        updatedAudioItem shouldNotBe audioItem
                        updatedAudioItem.id shouldBe audioItem.id
                        assertAudioItemChange(updatedAudioItem, audioItemChange, FlacTag().apply {
                            setField(FieldKey.ENCODER, audioItem.encoder)
                        })
                    }
                }
            }
        }

        and("a wav file") {
            checkAll(20, arbitraryWavTag, arbitraryAudioItemChange) { wavTag, audioItemChange ->

                val wavTempFile = tempfile(suffix = ".wav").also {
                    withContext(Dispatchers.IO) {
                        Files.copy(wavFile.toPath(), it.toPath(), StandardCopyOption.REPLACE_EXISTING)
                        AudioFileIO.read(it).apply {
                            tag = wavTag
                            commit()
                        }
                    }
                }

                When("An AudioItem is created") {
                    val audioItem = audioRepository.createFromFile(wavTempFile.toPath())
                    audioRepository.shouldContainExactly(audioItem)

                    assertSoftly {
                        audioItem.id shouldNotBe null
                        audioItem.encoding shouldBe "WAV PCM 16 bits"
                        audioItem.bitRate shouldBe 1411
                        audioItem.duration shouldBe Duration.ofSeconds(0)
                    }

                    then("its properties should match the metadata of the file") {
                        assertAudioItem(audioItem, wavTempFile.toPath(), wavTag)
                    }

                    then("its properties are modified when the audioItem is modified") {
                        audioRepository.editAudioItemMetadata(audioItem, audioItemChange)
                        val updatedAudioItem = audioRepository.findById(audioItem.id).get()

                        updatedAudioItem shouldNotBe audioItem
                        updatedAudioItem.id shouldBe audioItem.id
                        assertAudioItemChange(updatedAudioItem, audioItemChange, WavTag(WavOptions.READ_ID3_ONLY).apply {
                            iD3Tag = ID3v24Tag()
                            infoTag = WavInfoTag()
                            setField(FieldKey.ENCODER, audioItem.encoder)
                        })
                    }
                }
            }
        }
    }
})

fun setRandomAudioItemTagFields(tag: Tag, coverBytes: ByteArray): Tag {
    TagOptionSingleton.getInstance().isWriteMp4GenresAsText = true
    TagOptionSingleton.getInstance().isWriteMp3GenresAsText = true

    tag.setField(FieldKey.TITLE, Arb.string().next())
    tag.setField(FieldKey.ALBUM, Arb.string().next())
    tag.setField(FieldKey.COUNTRY, CountryCode.values().random().name)
    tag.setField(FieldKey.ALBUM_ARTIST, Arb.string().next())
    tag.setField(FieldKey.ARTIST, Arb.string().next())
    tag.setField(FieldKey.GENRE, Genre.values().random().name)
    tag.setField(FieldKey.COMMENT, Arb.string().next())
    tag.setField(FieldKey.GENRE, Arb.string().next())
    tag.setField(FieldKey.TRACK, Arb.short().next().toString())
    tag.setField(FieldKey.DISC_NO, Arb.short().next().toString())
    tag.setField(FieldKey.YEAR, Arb.short().next().toString())
    tag.setField(FieldKey.ENCODER, Arb.string().next())
    tag.setField(FieldKey.IS_COMPILATION, Arb.boolean().next().toString())
    if (tag is Mp4Tag) {
        tag.setField(FieldKey.BPM, Arb.int(-1, 220).next().toString())
    } else {
        tag.setField(FieldKey.BPM, Arb.float(-1.0f, 220.58f).next().toString())
    }
    setArtworkTag(tag, coverBytes)
    return tag
}

fun setArtworkTag(tag: Tag, coverBytes: ByteArray?) {
    File.createTempFile("tempCover", ".tmp").apply {
        Files.write(toPath(), coverBytes!!, StandardOpenOption.CREATE)
        deleteOnExit()
        ArtworkFactory.createArtworkFromFile(this).let { artwork ->
            tag.artworkList.clear()
            tag.addField(artwork)
        }
    }
}

fun assertAudioItemChange(audioItem: AudioItem, audioItemChange: AudioItemMetadataChange, tag: Tag) {
    tag.setField(FieldKey.TITLE, audioItemChange.title)
    tag.setField(FieldKey.ALBUM, audioItemChange.albumName)
    tag.setField(FieldKey.COUNTRY, audioItemChange.artist?.countryCode?.name)
    tag.setField(FieldKey.ALBUM_ARTIST, audioItemChange.albumArtist?.name)
    tag.setField(FieldKey.ARTIST, audioItemChange.artist?.name)
    tag.setField(FieldKey.GENRE, audioItemChange.genre?.name)
    tag.setField(FieldKey.COMMENT, audioItemChange.comments)
    tag.setField(FieldKey.GROUPING, audioItemChange.label?.name)
    tag.setField(FieldKey.TRACK, audioItemChange.trackNumber.toString())
    tag.setField(FieldKey.DISC_NO, audioItemChange.discNumber.toString())
    tag.setField(FieldKey.YEAR, audioItemChange.year.toString())
    if (tag is Mp4Tag) {
        val indexOfDot = audioItemChange.bpm.toString().indexOf('.')
        tag.setField(FieldKey.BPM, audioItemChange.bpm.toString().substring(0, indexOfDot))
    } else {
        tag.setField(FieldKey.BPM, audioItemChange.bpm.toString())
    }
    tag.setField(FieldKey.IS_COMPILATION, audioItemChange.isCompilation.toString())
    setArtworkTag(tag, audioItemChange.coverImage)
    assertAudioItem(audioItem, audioItem.path, tag)
}

fun assertAudioItem(audioItem: AudioItem, path: Path, tag: Tag) {
    assertSoftly {
        audioItem.path.absolutePathString() shouldBe path.absolutePathString()
        audioItem.title shouldBe tag.getFirst(FieldKey.TITLE)
        audioItem.album.name shouldBe tag.getFirst(FieldKey.ALBUM)
        audioItem.album.albumArtist.name shouldBe beautifyArtistName(tag.getFirst(FieldKey.ALBUM_ARTIST))
        audioItem.album.label.name shouldBe tag.getFirst(FieldKey.GROUPING)
        audioItem.album.label.countryCode shouldBe CountryCode.UNDEFINED
        audioItem.album.coverImage shouldBe tag.firstArtwork.binaryData
        audioItem.artist.name shouldBe beautifyArtistName(tag.getFirst(FieldKey.ARTIST))
        audioItem.artist.countryCode.name shouldBe tag.getFirst(FieldKey.COUNTRY)
        audioItem.genre shouldBe Genre.parseGenre(tag.getFirst(FieldKey.GENRE))
        audioItem.comments shouldBe tag.getFirst(FieldKey.COMMENT)
        audioItem.encoder shouldBe tag.getFirst(FieldKey.ENCODER)
        tag.getFirst(FieldKey.YEAR).toShortOrNull()?.let {
            if (it > 0)
                audioItem.album.year shouldBe it
            else
                audioItem.album.year shouldNotBe it
        }
        tag.getFirst(FieldKey.TRACK).toShortOrNull()?.let {
            if (it > 0)
                audioItem.trackNumber shouldBe it
            else
                audioItem.trackNumber shouldNotBe it
        }
        tag.getFirst(FieldKey.DISC_NO).toShortOrNull()?.let {
            if (it > 0)
                audioItem.discNumber shouldBe it
            else
                audioItem.discNumber shouldNotBe it
        }
        tag.getFirst(FieldKey.BPM).toFloatOrNull()?.let {
            if (it > 0)
                audioItem.bpm shouldBe it
            else
                audioItem.bpm shouldNotBe it
        }

        if (audioItem.extension == "m4a") {
            audioItem.album.isCompilation.shouldBe(tag.getFirst(FieldKey.IS_COMPILATION) == "1")
        } else {
            audioItem.album.isCompilation shouldBe tag.getFirst(FieldKey.IS_COMPILATION).toBoolean()
        }

        AudioFileIO.read(audioItem.path.toFile()).audioHeader.let {
            it.encodingType shouldBe audioItem.encoding
            Duration.ofSeconds(it.trackLength.toLong()) shouldBe audioItem.duration
            if (it.bitRate.first() == '~') {
                it.bitRate.substring(1).toInt() shouldBe audioItem.bitRate
            } else {
                it.bitRate.toInt() shouldBe audioItem.bitRate
            }
        }
    }
}