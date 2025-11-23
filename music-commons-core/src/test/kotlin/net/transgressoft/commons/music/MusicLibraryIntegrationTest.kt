package net.transgressoft.commons.music

import net.transgressoft.commons.event.ReactiveScope
import net.transgressoft.commons.music.audio.ArbitraryAudioFile.realAudioFile
import net.transgressoft.commons.music.audio.ArtistCatalog
import net.transgressoft.commons.music.audio.AudioItem
import net.transgressoft.commons.music.audio.AudioItemMapSerializer
import net.transgressoft.commons.music.audio.AudioLibrary
import net.transgressoft.commons.music.audio.DefaultAudioLibrary
import net.transgressoft.commons.music.playlist.AudioPlaylistMapSerializer
import net.transgressoft.commons.music.playlist.DefaultPlaylistHierarchy
import net.transgressoft.commons.music.playlist.MutableAudioPlaylist
import net.transgressoft.commons.music.playlist.PlaylistHierarchy
import net.transgressoft.commons.music.playlist.asJsonKeyValues
import net.transgressoft.commons.music.waveform.AudioWaveform
import net.transgressoft.commons.music.waveform.AudioWaveformMapSerializer
import net.transgressoft.commons.music.waveform.AudioWaveformRepository
import net.transgressoft.commons.music.waveform.DefaultAudioWaveformRepository
import net.transgressoft.commons.persistence.json.JsonFileRepository
import net.transgressoft.commons.persistence.json.JsonRepository
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.spec.tempfile
import io.kotest.matchers.collections.shouldContainOnly
import io.kotest.matchers.optional.shouldBePresent
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.property.Arb
import io.kotest.property.arbitrary.next
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.test.UnconfinedTestDispatcher

@ExperimentalCoroutinesApi
internal class MusicLibraryIntegrationTest : StringSpec({

    val testDispatcher = UnconfinedTestDispatcher()
    val testScope = CoroutineScope(testDispatcher)

    val audioFile = tempfile("audioLibrary-test", ".json").apply { deleteOnExit() }
    val playlistsFile = tempfile("playlistHierarchy-test", ".json").apply { deleteOnExit() }
    val waveformsFile = tempfile("waveformRepository-test", ".json").apply { deleteOnExit() }

    lateinit var audioLibraryRepository: JsonRepository<Int, AudioItem>
    lateinit var playlistHierarchyRepository: JsonRepository<Int, MutableAudioPlaylist>
    lateinit var waveformsRepository: JsonRepository<Int, AudioWaveform>

    lateinit var audioLibrary: AudioLibrary<AudioItem, ArtistCatalog<AudioItem>>
    lateinit var waveforms: AudioWaveformRepository<AudioWaveform, AudioItem>
    lateinit var playlistHierarchy: PlaylistHierarchy<AudioItem, MutableAudioPlaylist>

    beforeSpec {
        ReactiveScope.flowScope = testScope
        ReactiveScope.ioScope = testScope
    }

    beforeEach {
        audioLibraryRepository = JsonFileRepository(audioFile, AudioItemMapSerializer)
        waveformsRepository = JsonFileRepository(waveformsFile, AudioWaveformMapSerializer)
        playlistHierarchyRepository = JsonFileRepository(playlistsFile, AudioPlaylistMapSerializer)

        audioLibrary = DefaultAudioLibrary(audioLibraryRepository)
        waveforms = DefaultAudioWaveformRepository(waveformsRepository)
        playlistHierarchy = DefaultPlaylistHierarchy(playlistHierarchyRepository)
    }

    afterEach {
        audioLibraryRepository.close()
        waveformsRepository.close()
        playlistHierarchyRepository.close()
    }

    afterSpec {
        ReactiveScope.resetDefaultFlowScope()
        ReactiveScope.resetDefaultIoScope()
    }

    "Operations on audio items impact subscribed repositories" {
        audioLibrary.subscribe(waveforms.audioItemEventSubscriber)
        audioLibrary.subscribe(playlistHierarchy.audioItemEventSubscriber)

        val audioItem = audioLibrary.createFromFile(Arb.realAudioFile().next())

        testDispatcher.scheduler.advanceUntilIdle()

        audioFile.readText() shouldContain audioItem.path.toString()
        audioLibrary.findAlbumAudioItems(audioItem.artist, audioItem.album.name).shouldContainOnly(audioItem)

        val waveform = waveforms.getOrCreateWaveformAsync(audioItem, 780, 335, testDispatcher.asExecutor())

        testDispatcher.scheduler.advanceUntilIdle()

        waveform.get() shouldNotBe null
        waveform.get().id shouldBe audioItem.id
        waveformsFile.readText() shouldContain audioItem.path.toString()
        waveformsFile.readText() shouldContain waveform.get().id.toString()

        playlistHierarchy.createPlaylist("Test Playlist").also { it.addAudioItem(audioItem) }

        testDispatcher.scheduler.advanceUntilIdle()

        playlistsFile.readText() shouldContain "Test Playlist"
        playlistsFile.readText() shouldContain audioItem.id.toString()

        audioItem.title = "New title"
        testDispatcher.scheduler.advanceUntilIdle()

        audioLibrary.contains { it.title == "New title" }
        audioLibrary.size() shouldBe 1
        audioLibrary.findAlbumAudioItems(audioItem.artist, audioItem.album.name).shouldContainOnly(audioItem)

        audioFile.readText() shouldContain "New title"
        val updatedPlaylist = playlistHierarchy.findByName("Test Playlist").get()
        updatedPlaylist.audioItems.contains(audioItem) shouldBe true

        audioLibrary.remove(audioItem) shouldBe true
        audioLibrary.isEmpty shouldBe true

        testDispatcher.scheduler.advanceUntilIdle()

        audioLibrary.findAlbumAudioItems(audioItem.artist, audioItem.album.name).isEmpty() shouldBe true
        audioFile.readText() shouldBe "{}"

        playlistHierarchy.findByName("Test Playlist") shouldBePresent {
            it.audioItems.isEmpty() shouldBe true
            playlistsFile.readText() shouldEqualJson listOf(it).asJsonKeyValues()
        }

        waveforms.isEmpty shouldBe true
        waveformsFile.readText() shouldBe "{}"
    }
})