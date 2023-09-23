package net.transgressoft.commons.music.audio

import net.transgressoft.commons.music.AudioUtils.UNASSIGNED_ID
import net.transgressoft.commons.music.AudioUtils.beautifyArtistName
import net.transgressoft.commons.music.audio.AudioItemTestUtil.arbitraryAudioItem
import net.transgressoft.commons.music.audio.AudioItemTestUtil.arbitraryAudioItemChange
import net.transgressoft.commons.music.audio.AudioItemTestUtil.arbitraryFlacFile
import net.transgressoft.commons.music.audio.AudioItemTestUtil.arbitraryM4aFile
import net.transgressoft.commons.music.audio.AudioItemTestUtil.arbitraryMp3File
import net.transgressoft.commons.music.audio.AudioItemTestUtil.arbitraryWavFile
import net.transgressoft.commons.music.audio.AudioItemTestUtil.setArtworkTag
import net.transgressoft.commons.music.audio.AudioItemTestUtil.tag
import com.neovisionaries.i18n.CountryCode
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.timing.eventually
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.engine.spec.tempfile
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveAtMostSize
import io.kotest.matchers.optional.shouldBePresent
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.next
import io.kotest.property.arbitrary.set
import io.kotest.property.checkAll
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.audio.wav.WavOptions
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.flac.FlacTag
import org.jaudiotagger.tag.id3.ID3v24Tag
import org.jaudiotagger.tag.mp4.Mp4Tag
import org.jaudiotagger.tag.wav.WavInfoTag
import org.jaudiotagger.tag.wav.WavTag
import java.io.File
import java.nio.file.Path
import java.time.Duration
import kotlin.io.path.absolutePathString
import kotlin.time.Duration.Companion.seconds

internal class AudioItemJsonRepositoryTest : BehaviorSpec({

    lateinit var jsonFile: File
    lateinit var audioRepository: AudioItemRepository<MutableAudioItem>

    fun Album.audioItems(): Set<AudioItem> = audioRepository.search { it.album == this }.toSet()

    beforeContainer {
        jsonFile = tempfile("audioItemRepository-test", ".json").also { it.deleteOnExit() }
        audioRepository = AudioItemJsonRepository("AudioRepo", jsonFile)
    }

    given("An AudioItemRepository") {

        and("A mp3 file") {
            checkAll(10, arbitraryMp3File, arbitraryAudioItemChange) { mp3File, audioItemChange ->
                When("An AudioItem is created") {
                    val audioItem = audioRepository.createFromFile(mp3File.toPath())
                    audioItem.id shouldNotBe UNASSIGNED_ID
                    audioRepository.add(audioItem) shouldBe false
                    audioRepository.contains { it == audioItem } shouldBe true
                    audioRepository.findByUniqueId(audioItem.uniqueId) shouldBePresent { found -> found shouldBe audioItem }

                    then("it is possible to query the repository by its artist") {
                        audioRepository.containsAudioItemWithArtist(audioItem.artist.name) shouldBe true
                        audioRepository.containsAudioItemWithArtist(audioItem.album.albumArtist.name) shouldBe true
                        audioRepository.artistCatalogRegistry.findFirst(audioItem.artist.name) shouldBePresent {
                            it.artist shouldBe audioItem.artist
                            it.containsAudioItem(audioItem)
                        }
                    }

                    then("its properties should match the metadata of the file") {
                        assertSoftly {
                            audioItem.id shouldNotBe null
                            audioItem.encoding shouldBe "MPEG-1 Layer 3"
                            audioItem.bitRate shouldBe 143
                            audioItem.duration shouldBe Duration.ofSeconds(8)
                        }
                        assertAudioItem(audioItem, mp3File.toPath(), mp3File.tag())
                    }

                    then("its properties are modified when the audioItem is modified") {
                        val updatedAudioItem = audioItem.update(audioItemChange)
                        audioRepository.addOrReplace(updatedAudioItem) shouldBe false
                        audioRepository.findById(audioItem.id) shouldBePresent {
                            assertAudioItemChange(it, audioItemChange, ID3v24Tag().apply {
                                setField(FieldKey.ENCODER, audioItem.encoder)
                            })
                        }
                    }
                }
            }
        }

        and("A m4a file") {
            checkAll(5, arbitraryM4aFile, arbitraryAudioItemChange) { m4aFile, audioItemChange ->
                When("An AudioItem is created") {
                    val audioItem = audioRepository.createFromFile(m4aFile.toPath())
                    audioItem.id shouldNotBe UNASSIGNED_ID
                    audioRepository.add(audioItem) shouldBe false
                    audioRepository.contains { it == audioItem } shouldBe true
                    audioRepository.findByUniqueId(audioItem.uniqueId) shouldBePresent { found -> found shouldBe audioItem }

                    then("its properties should match the metadata of the file") {
                        assertSoftly {
                            audioItem.id shouldNotBe null
                            audioItem.encoding shouldBe "Aac"
                            audioItem.bitRate shouldBe 256
                            audioItem.duration shouldBe Duration.ofSeconds(42)
                        }
                        assertAudioItem(audioItem, m4aFile.toPath(), m4aFile.tag())
                    }

                    then("its properties are modified when the audioItem is modified") {
                        val updatedAudioItem = audioItem.update(audioItemChange)
                        audioRepository.addOrReplace(updatedAudioItem) shouldBe false
                        audioRepository.findById(audioItem.id) shouldBePresent {
                            assertAudioItemChange(it, audioItemChange, Mp4Tag().apply {
                                setField(FieldKey.ENCODER, audioItem.encoder)
                            })
                        }
                    }
                }
            }
        }

        and("A flac file") {
            checkAll(5, arbitraryFlacFile, arbitraryAudioItemChange) { flacFile, audioItemChange ->
                When("An AudioItem is created") {
                    val audioItem = audioRepository.createFromFile(flacFile.toPath())
                    audioItem.id shouldNotBe UNASSIGNED_ID
                    audioRepository.add(audioItem) shouldBe false
                    audioRepository.contains { it == audioItem } shouldBe true
                    audioRepository.findByUniqueId(audioItem.uniqueId) shouldBePresent { found -> found shouldBe audioItem }

                    then("its properties should match the metadata of the file") {
                        assertAudioItem(audioItem, flacFile.toPath(), flacFile.tag())
                        assertSoftly {
                            audioItem.id shouldNotBe null
                            audioItem.encoding shouldBe "FLAC 16 bits"
                            audioItem.bitRate shouldBe 445
                            audioItem.duration shouldBe Duration.ofSeconds(28)
                        }
                    }

                    then("its properties are modified when the audioItem is modified") {
                        val updatedAudioItem = audioItem.update(audioItemChange)
                        audioRepository.addOrReplace(updatedAudioItem) shouldBe false
                        audioRepository.findById(audioItem.id) shouldBePresent {
                            assertAudioItemChange(it, audioItemChange, FlacTag().apply {
                                setField(FieldKey.ENCODER, audioItem.encoder)
                            })
                        }
                    }
                }
            }
        }

        and("A wav file") {
            checkAll(5, arbitraryWavFile, arbitraryAudioItemChange) { wavFile, audioItemChange ->
                When("An AudioItem is created") {
                    val audioItem = audioRepository.createFromFile(wavFile.toPath())
                    audioItem.id shouldNotBe UNASSIGNED_ID
                    audioRepository.add(audioItem) shouldBe false
                    audioRepository.contains { it == audioItem } shouldBe true
                    audioRepository.findByUniqueId(audioItem.uniqueId) shouldBePresent { found -> found shouldBe audioItem }

                    assertSoftly {
                        audioItem.id shouldNotBe null
                        audioItem.encoding shouldBe "WAV PCM 16 bits"
                        audioItem.bitRate shouldBe 1411
                        audioItem.duration shouldBe Duration.ofSeconds(0)
                    }

                    then("its properties should match the metadata of the file") {
                        assertAudioItem(audioItem, wavFile.toPath(), wavFile.tag())
                    }

                    then("its properties are modified when the audioItem is modified") {
                        val updatedAudioItem = audioItem.update(audioItemChange)
                        audioRepository.addOrReplace(updatedAudioItem) shouldBe false
                        audioRepository.findById(audioItem.id) shouldBePresent {
                            assertAudioItemChange(it, audioItemChange, WavTag(WavOptions.READ_ID3_ONLY).apply {
                                iD3Tag = ID3v24Tag()
                                infoTag = WavInfoTag()
                                setField(FieldKey.ENCODER, audioItem.encoder)
                            })
                        }
                    }
                }
            }
        }

        and("A created AudioItem added to the repository") {

            checkAll(1, arbitraryMp3File) { mp3File ->
                val audioItem = audioRepository.createFromFile(mp3File.toPath())
                audioItem.id shouldNotBe UNASSIGNED_ID
                audioRepository.add(audioItem) shouldBe false
                audioRepository.contains { it == audioItem } shouldBe true
                audioRepository.findByUniqueId(audioItem.uniqueId) shouldBePresent { found -> found shouldBe audioItem }
                eventually(2.seconds) {

                val loadedRepository = AudioItemJsonRepository("AudioRepo", jsonFile)
                    loadedRepository.size() shouldBe 1
                    loadedRepository.findById(audioItem.id) shouldBePresent {
                        it shouldBe audioItem
                        it.id shouldNotBe UNASSIGNED_ID
                    }
                    loadedRepository shouldBe audioRepository
                }

                then("`add` with same id does not replace the previous one") {
                    val arbAudioItem = arbitraryAudioItem(id = audioItem.id, album = audioItem.album).next()
                    audioRepository.add(arbAudioItem) shouldBe false
                    audioItem.album.audioItems().shouldContainExactlyInAnyOrder(audioItem)
                    audioRepository.findById(audioItem.id) shouldBePresent { found -> found.id shouldBe audioItem.id }
                }

                then("`add` with same album allows to query all items from the same album") {
                    val arbAudioItem = arbitraryAudioItem(album = audioItem.album).next()
                    audioRepository.size() shouldBe 1
                    audioRepository.add(arbAudioItem) shouldBe true
                    audioRepository.size() shouldBe 2
                    audioRepository.findById(arbAudioItem.id) shouldBePresent { found -> found.album shouldBe audioItem.album }
                    audioItem.album.audioItems().shouldContainExactlyInAnyOrder(audioItem, arbAudioItem)
                    audioRepository.contains { audioItem == it}
                    audioRepository.contains { arbAudioItem == it}
                }

                then("`addOrReplace` with same id does replace the previous one") {
                    val audioItemModified = audioItem.update { title = "New title" }
                    audioRepository.size() shouldBe 2
                    audioRepository.addOrReplace(audioItemModified) shouldBe false
                    audioItem.album.audioItems().shouldContain(audioItemModified)
                    audioRepository.findById(audioItem.id) shouldBePresent { found -> found.id shouldBe audioItem.id }
                    audioRepository.contains { it.title == "New title" } shouldBe true

                    eventually(2.seconds) {
                        val loadedRepository = AudioItemJsonRepository("AudioRepo", jsonFile)
                        loadedRepository.size() shouldBe 2
                        loadedRepository.findById(audioItem.id) shouldBePresent {
                            it shouldBe audioItemModified
                            it.title shouldBe "New title"
                        }
                    }
                }

                then("`addOrReplaceAll works as expected`") {
                    val size = audioRepository.size()
                    val arbAudioItems = Arb.set(arbitraryAudioItem(), 5..10).next()
                    audioRepository.addOrReplaceAll(arbAudioItems) shouldBe true
                    audioRepository.size() shouldBe arbAudioItems.size + size
                }

                then("removing one or several audio items works as expected") {
                    val size = audioRepository.size()
                    audioRepository.remove(audioItem) shouldBe true
                    audioRepository.size() shouldBe size - 1

                    val arbAudioItems = Arb.set(arbitraryAudioItem(), 5..10).next()
                    assertSoftly {
                        audioRepository.addOrReplaceAll(arbAudioItems) shouldBe true
                        audioRepository.size() shouldBe size - 1 + arbAudioItems.size
                        audioRepository.removeAll(arbAudioItems) shouldBe true
                        audioRepository.size() shouldBe size - 1
                        audioRepository.clear()
                        audioRepository.isEmpty shouldBe true
                    }
                }
            }
        }

        and("A bunch of AudioItems of the same Artist") {
            checkAll(5, Arb.set(arbitraryAudioItem(artist = ImmutableArtist("Queen")), 5, 25)) { bunchOfAudioItems ->

                audioRepository.addOrReplaceAll(bunchOfAudioItems)

                then("the repository can return a list of random audio items from the Artist") {
                    val itemsFromArtist = audioRepository.getRandomAudioItemsFromArtist("Queen", 25)
                    itemsFromArtist shouldHaveAtMostSize 25
                    itemsFromArtist.forAll { it.artist shouldBe ImmutableArtist("Queen") }

                    val itemsFromArtist2 = audioRepository.getRandomAudioItemsFromArtist("Queen", 25)
                    itemsFromArtist shouldHaveAtMostSize 25
                    itemsFromArtist.forAll { it.artist shouldBe ImmutableArtist("Queen") }
                    itemsFromArtist shouldNotBe itemsFromArtist2
                }
            }
        }
    }
})

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
    audioItem.path.absolutePathString() shouldBe path.absolutePathString()
    audioItem.title shouldBe tag.getFirst(FieldKey.TITLE)
    audioItem.album.name shouldBe tag.getFirst(FieldKey.ALBUM)
    audioItem.album.albumArtist.name shouldBe beautifyArtistName(tag.getFirst(FieldKey.ALBUM_ARTIST))
    audioItem.album.label.name shouldBe tag.getFirst(FieldKey.GROUPING)
    audioItem.album.label.countryCode shouldBe CountryCode.UNDEFINED
    audioItem.coverImage shouldBe tag.firstArtwork.binaryData
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