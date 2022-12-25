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
import org.jaudiotagger.tag.flac.FlacTag
import org.jaudiotagger.tag.id3.ID3v24Tag
import org.jaudiotagger.tag.images.ArtworkFactory
import org.jaudiotagger.tag.mp4.Mp4Tag
import org.jaudiotagger.tag.wav.WavInfoTag
import org.jaudiotagger.tag.wav.WavTag
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.time.Duration
import kotlin.io.path.absolutePathString

internal class AudioItemInMemoryRepositoryTest2 : BehaviorSpec({

    fun String.asURI(): URI = object {}.javaClass.getResource(this)!!.toURI()

    val mp3File = File("/testfiles/testeable.mp3".asURI())
    val m4aFile = File("/testfiles/testeable.m4a".asURI())
    val flacFile = File("/testfiles/testeable.flac".asURI())
    val wavFile = File("/testfiles/testeable.wav".asURI())
    val testCover = File("/testfiles/cover.jpg".asURI())
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
            artist = ImmutableArtist(Arb.string().bind()),
            albumName = Arb.string().bind(),
            albumArtist = ImmutableArtist(Arb.string().bind()),
            isCompilation = Arb.boolean().bind(),
            year = Arb.short().bind(),
            label = ImmutableLabel(Arb.string().bind()),
            coverImage = Arb.byteArray(Arb.int(16, 32), Arb.byte()).bind(),
            genre = Genre.UNDEFINED,
            comments = Arb.string().bind(),
            trackNumber = Arb.short().bind(),
            discNumber = Arb.short().bind(),
            bpm = Arb.float().bind()
        )
    }
    lateinit var audioRepository: AudioItemRepository<AudioItem>
    lateinit var audioItem: AudioItem

    beforeContainer {
        audioRepository = AudioItemInMemoryRepository()
    }

    given("An AudioItemRepository") {
        val audioRepository = AudioItemInMemoryRepository()

        and("A mp3 file") {
            checkAll(2, arbitraryMp3Tag, arbitraryAudioItemChange) { mp3Tag, audioItemChange ->

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

                    and("it is modified") {
                        audioRepository.shouldContainExactly(audioItem)
                        audioRepository.editAudioItemMetadata(audioItem, audioItemChange)

                        then("its properties are modified") {
                            val updatedAudioItem = audioRepository.findById(audioItem.id).get()
                            assertSoftly {
                                updatedAudioItem shouldNotBe audioItem
                                updatedAudioItem.id shouldBe audioItem.id
                                updatedAudioItem.path shouldBe audioItem.path
                                updatedAudioItem.encoder shouldBe audioItem.encoder
                                updatedAudioItem.encoding shouldBe audioItem.encoding
                                updatedAudioItem.bitRate shouldBe audioItem.bitRate
                            }
                            assertAudioItemChange(updatedAudioItem, audioItemChange, mp3Tag)
                        }
                    }
                }
            }
        }

        and("A m4a file") {
            checkAll(2, arbitraryM4aTag, arbitraryAudioItemChange) { m4aTag, audioItemChange ->

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
                            audioItem.encoding shouldBe "Aac"
                            audioItem.bitRate shouldBe 256
                            audioItem.duration shouldBe Duration.ofSeconds(296)
                        }
                        assertAudioItem(audioItem, m4aTempFile.toPath(), m4aTag)
                    }
                }

                When("An AudioItem is modified") {
                    val audioItem = audioRepository.createFromFile(m4aTempFile.toPath())
                    audioRepository.shouldContainExactly(audioItem)
                    audioRepository.editAudioItemMetadata(audioItem, audioItemChange)

                    then("its properties are modified") {
                        val updatedAudioItem = audioRepository.findById(audioItem.id).get()
                        assertSoftly {
                            updatedAudioItem shouldNotBe audioItem
                            updatedAudioItem.id shouldBe audioItem.id
                            updatedAudioItem.path shouldBe audioItem.path
                            updatedAudioItem.encoder shouldBe audioItem.encoder
                            updatedAudioItem.encoding shouldBe audioItem.encoding
                            updatedAudioItem.bitRate shouldBe audioItem.bitRate
                        }
                        assertAudioItemChange(updatedAudioItem, audioItemChange, m4aTag)
                    }
                }
            }
        }

        and("a flac file") {
            checkAll(2, arbitraryFlacTag, arbitraryAudioItemChange) { flacTag, audioItemChange ->

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
                            audioItem.encoding shouldBe "FLAC 16 bits"
                            audioItem.bitRate shouldBe 689
                            audioItem.duration shouldBe Duration.ofSeconds(30)
                        }
                    }
                }

                When("An AudioItem is modified") {
                    val audioItem = audioRepository.createFromFile(flacTempFile.toPath())
                    audioRepository.shouldContainExactly(audioItem)
                    audioRepository.editAudioItemMetadata(audioItem, audioItemChange)

                    then("its properties are modified") {
                        val updatedAudioItem = audioRepository.findById(audioItem.id).get()
                        assertSoftly {
                            updatedAudioItem shouldNotBe audioItem
                            updatedAudioItem.id shouldBe audioItem.id
                            updatedAudioItem.path shouldBe audioItem.path
                            updatedAudioItem.encoder shouldBe audioItem.encoder
                            updatedAudioItem.encoding shouldBe audioItem.encoding
                            updatedAudioItem.bitRate shouldBe audioItem.bitRate
                        }
                        assertAudioItemChange(updatedAudioItem, audioItemChange, flacTag)
                    }
                }
            }
        }

        and("a wav file") {
            checkAll(2, arbitraryWavTag, arbitraryAudioItemChange) { wavTag, audioItemChange ->

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
                        audioItem.encoding shouldBe "WAV PCM 24 bits"
                        audioItem.bitRate shouldBe 2116
                        audioItem.duration shouldBe Duration.ofSeconds(104)
                    }

                    then("its properties should match the metadata of the file") {
                        assertAudioItem(audioItem, wavTempFile.toPath(), wavTag)
                    }
                }

                When("An AudioItem is modified") {
                    val audioItem = audioRepository.createFromFile(wavFile.toPath())
                    audioRepository.shouldContainExactly(audioItem)
                    audioRepository.editAudioItemMetadata(audioItem, audioItemChange)

                    then("its properties are modified") {
                        val updatedAudioItem = audioRepository.findById(audioItem.id).get()
                        assertSoftly {
                            updatedAudioItem shouldNotBe audioItem
                            updatedAudioItem.id shouldBe audioItem.id
                            updatedAudioItem.path shouldBe audioItem.path
                            updatedAudioItem.encoder shouldBe audioItem.encoder
                            updatedAudioItem.encoding shouldBe audioItem.encoding
                            updatedAudioItem.bitRate shouldBe audioItem.bitRate
                        }
                        assertAudioItemChange(updatedAudioItem, audioItemChange, wavTag)
                    }
                }
            }
        }
    }
})

fun setRandomAudioItemTagFields(tag: Tag, coverBytes: ByteArray): Tag {
    tag.setField(FieldKey.TITLE, Arb.string().next())
    tag.setField(FieldKey.ARTIST, Arb.string().next())
    tag.setField(FieldKey.ALBUM, Arb.string().next())
    tag.setField(FieldKey.ALBUM_ARTISTS, Arb.string().next())
    tag.setField(FieldKey.ALBUM_YEAR, Arb.string().next())
    tag.setField(FieldKey.GROUPING, Arb.string().next())
    tag.setField(FieldKey.COMMENT, Arb.string().next())
    tag.setField(FieldKey.GENRE, Arb.string().next())
    tag.setField(FieldKey.TRACK, Arb.positiveShort().next().toString())
    tag.setField(FieldKey.DISC_NO, Arb.positiveShort().next().toString())
    tag.setField(FieldKey.BPM, Arb.int(85..200).next().toString())
    tag.setField(FieldKey.ENCODER, Arb.string().next())
    tag.setField(FieldKey.IS_COMPILATION, Arb.boolean().next().toString())
    setArtworkTag(tag, coverBytes)
    return tag
}

fun setArtworkTag(tag: Tag, coverBytes: ByteArray?) {
    File.createTempFile("tempCover", ".tmp").apply {
        Files.write(toPath(), coverBytes!!, StandardOpenOption.CREATE)
        deleteOnExit()
        ArtworkFactory.createArtworkFromFile(this).let { artwork ->
            tag.addField(artwork)
        }
    }
}

fun assertAudioItemChange(audioItem: AudioItem, audioItemChange: AudioItemMetadataChange, tag: Tag) {
    assertSoftly {
        audioItem.title shouldBe audioItemChange.title
        audioItem.artist shouldBe audioItemChange.artist
        audioItem.album.name shouldBe audioItemChange.albumName
        audioItem.album.albumArtist shouldBe audioItemChange.albumArtist
        audioItem.album.year shouldBe audioItemChange.year
        audioItem.album.isCompilation shouldBe audioItemChange.isCompilation
        audioItem.album.label shouldBe audioItemChange.label
        audioItem.album.coverImage shouldBe audioItemChange.coverImage
        audioItem.trackNumber shouldBe audioItemChange.trackNumber
        audioItem.discNumber shouldBe audioItemChange.discNumber
        audioItem.bpm shouldBe audioItemChange.bpm
    }
    tag.setField(FieldKey.TITLE, audioItemChange.title)
    tag.setField(FieldKey.ARTIST, audioItemChange.artist?.name)
    tag.setField(FieldKey.ALBUM, audioItemChange.albumName)
    tag.setField(FieldKey.COMMENT, audioItemChange.comments)
    tag.setField(FieldKey.ALBUM_ARTIST, audioItemChange.albumArtist?.name)
    tag.setField(FieldKey.YEAR, audioItemChange.year.toString())
    tag.setField(FieldKey.IS_COMPILATION, audioItemChange.isCompilation.toString())
    tag.setField(FieldKey.GROUPING, audioItemChange.label?.name)
    tag.setField(FieldKey.TRACK, audioItemChange.trackNumber.toString())
    tag.setField(FieldKey.DISC_NO, audioItemChange.discNumber.toString())
    tag.setField(FieldKey.BPM, audioItemChange.bpm.toString())
    setArtworkTag(tag, audioItemChange.coverImage)
    assertAudioItem(audioItem, audioItem.path, tag)
}

fun assertAudioItem(audioItem: AudioItem, path: Path, tag: Tag) {
    assertSoftly {
        audioItem.path.absolutePathString() shouldBe path.absolutePathString()
        audioItem.title shouldBe tag.getFirst(FieldKey.TITLE)
        audioItem.album.name shouldBe tag.getFirst(FieldKey.ALBUM)
        audioItem.album.albumArtist.name shouldBe beautifyArtistName(tag.getFirst(FieldKey.ALBUM_ARTIST))
        audioItem.artist.name shouldBe beautifyArtistName(tag.getFirst(FieldKey.ARTIST))
        audioItem.artist.countryCode shouldBe CountryCode.UNDEFINED
        audioItem.genre shouldBe Genre.UNDEFINED
        audioItem.comments shouldBe tag.getFirst(FieldKey.COMMENT)
        audioItem.album.label.name shouldBe tag.getFirst(FieldKey.GROUPING)
        audioItem.album.label.countryCode shouldBe CountryCode.UNDEFINED
        audioItem.trackNumber shouldBe tag.getFirst(FieldKey.TRACK).toShortOrNull()
        audioItem.discNumber shouldBe tag.getFirst(FieldKey.DISC_NO).toShortOrNull()
        audioItem.album.year shouldBe tag.getFirst(FieldKey.YEAR).toShortOrNull()
        audioItem.bpm shouldBe tag.getFirst(FieldKey.BPM).toFloatOrNull()
        audioItem.encoder shouldBe tag.getFirst(FieldKey.ENCODER)
        audioItem.album.coverImage shouldBe tag.firstArtwork.binaryData

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