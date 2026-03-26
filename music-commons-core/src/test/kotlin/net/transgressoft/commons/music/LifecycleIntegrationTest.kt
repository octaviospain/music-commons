package net.transgressoft.commons.music

import net.transgressoft.commons.music.audio.ArbitraryAudioFile.realAudioFile
import net.transgressoft.commons.music.audio.AudioItem
import net.transgressoft.commons.music.audio.AudioItemMapSerializer
import net.transgressoft.commons.music.audio.DefaultAudioLibrary
import net.transgressoft.commons.music.playlist.AudioPlaylistMapSerializer
import net.transgressoft.commons.music.playlist.DefaultPlaylistHierarchy
import net.transgressoft.commons.music.playlist.MutableAudioPlaylist
import net.transgressoft.commons.music.waveform.AudioWaveform
import net.transgressoft.commons.music.waveform.AudioWaveformMapSerializer
import net.transgressoft.commons.music.waveform.DefaultAudioWaveformRepository
import net.transgressoft.commons.music.waveform.ScalableAudioWaveform
import net.transgressoft.lirp.event.ReactiveScope
import net.transgressoft.lirp.persistence.json.JsonFileRepository
import net.transgressoft.lirp.persistence.json.JsonRepository
import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.spec.tempfile
import io.kotest.matchers.optional.shouldBePresent
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.next
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher

/**
 * Integration tests verifying that [AutoCloseable.close] stops event propagation
 * across audio libraries, playlist hierarchies, and waveform repositories.
 */
@ExperimentalCoroutinesApi
internal class LifecycleIntegrationTest : StringSpec({

    val testDispatcher = UnconfinedTestDispatcher()
    val testScope = CoroutineScope(testDispatcher)

    lateinit var audioFile: java.io.File
    lateinit var playlistsFile: java.io.File
    lateinit var waveformsFile: java.io.File

    lateinit var audioLibraryRepository: JsonRepository<Int, AudioItem>
    lateinit var playlistHierarchyRepository: JsonRepository<Int, MutableAudioPlaylist>
    lateinit var waveformsRepository: JsonRepository<Int, AudioWaveform>

    lateinit var audioLibrary: DefaultAudioLibrary
    lateinit var waveforms: DefaultAudioWaveformRepository<AudioItem>
    lateinit var playlistHierarchy: DefaultPlaylistHierarchy

    beforeSpec {
        ReactiveScope.flowScope = testScope
        ReactiveScope.ioScope = testScope
    }

    beforeEach {
        audioFile = tempfile("lifecycle-audioLibrary", ".json").also { it.deleteOnExit() }
        playlistsFile = tempfile("lifecycle-playlists", ".json").also { it.deleteOnExit() }
        waveformsFile = tempfile("lifecycle-waveforms", ".json").also { it.deleteOnExit() }

        audioLibraryRepository = JsonFileRepository(audioFile, AudioItemMapSerializer)
        waveformsRepository = JsonFileRepository(waveformsFile, AudioWaveformMapSerializer)
        playlistHierarchyRepository = JsonFileRepository(playlistsFile, AudioPlaylistMapSerializer)

        audioLibrary = DefaultAudioLibrary(audioLibraryRepository)
        waveforms = DefaultAudioWaveformRepository(waveformsRepository)
        playlistHierarchy = DefaultPlaylistHierarchy(playlistHierarchyRepository)
    }

    afterEach {
        audioLibrary.close()
        waveforms.close()
        playlistHierarchy.close()
        audioLibraryRepository.close()
        waveformsRepository.close()
        playlistHierarchyRepository.close()
    }

    afterSpec {
        ReactiveScope.resetDefaultFlowScope()
        ReactiveScope.resetDefaultIoScope()
    }

    "AudioLibrary close() stops reacting to repository events" {
        val audioItem = audioLibrary.createFromFile(Arb.realAudioFile().next())
        testDispatcher.scheduler.advanceUntilIdle()

        audioLibrary.findAlbumAudioItems(audioItem.artist, audioItem.album.name).size shouldBe 1

        audioLibrary.close()

        val audioItem2 = audioLibrary.createFromFile(Arb.realAudioFile().next())
        testDispatcher.scheduler.advanceUntilIdle()

        // After close(), the artist catalog registry subscription is cancelled
        // so newly added items are not indexed in the catalog
        audioLibrary.findAlbumAudioItems(audioItem2.artist, audioItem2.album.name).any { it.id == audioItem2.id } shouldBe false
        audioLibrary.size() shouldBe 2
    }

    "PlaylistHierarchy close() stops reacting to audio item deletions" {
        audioLibrary.subscribe(playlistHierarchy)

        val audioItem = audioLibrary.createFromFile(Arb.realAudioFile().next())
        testDispatcher.scheduler.advanceUntilIdle()

        val playlist = playlistHierarchy.createPlaylist("Lifecycle Test Playlist")
        playlist.addAudioItem(audioItem)
        testDispatcher.scheduler.advanceUntilIdle()

        playlist.audioItems.size shouldBe 1

        playlistHierarchy.close()

        audioLibrary.remove(audioItem) shouldBe true
        testDispatcher.scheduler.advanceUntilIdle()

        playlistHierarchy.findByName("Lifecycle Test Playlist") shouldBePresent {
            it.audioItems.any { item -> item.id == audioItem.id } shouldBe true
        }
    }

    "DefaultAudioWaveformRepository close() stops reacting to audio item deletions" {
        audioLibrary.subscribe(waveforms)

        val audioItem = audioLibrary.createFromFile(Arb.realAudioFile().next())
        testDispatcher.scheduler.advanceUntilIdle()

        val waveform = ScalableAudioWaveform(audioItem.id, audioItem.path)
        waveforms.add(waveform) shouldBe true
        testDispatcher.scheduler.advanceUntilIdle()

        waveforms.size() shouldBe 1

        waveforms.close()

        audioLibrary.remove(audioItem) shouldBe true
        testDispatcher.scheduler.advanceUntilIdle()

        waveforms.size() shouldBe 1
        waveforms.findById(audioItem.id) shouldBePresent { it shouldBe waveform }
    }

    "Full lifecycle cleanup closes all components and retains data" {
        audioLibrary.subscribe(waveforms)
        audioLibrary.subscribe(playlistHierarchy)

        val audioItem = audioLibrary.createFromFile(Arb.realAudioFile().next())
        testDispatcher.scheduler.advanceUntilIdle()

        val playlist = playlistHierarchy.createPlaylist("Full Lifecycle Playlist")
        playlist.addAudioItem(audioItem)
        testDispatcher.scheduler.advanceUntilIdle()

        val waveform = ScalableAudioWaveform(audioItem.id, audioItem.path)
        waveforms.add(waveform) shouldBe true
        testDispatcher.scheduler.advanceUntilIdle()

        audioLibrary.close()
        playlistHierarchy.close()
        waveforms.close()

        audioLibrary.remove(audioItem) shouldBe true
        testDispatcher.scheduler.advanceUntilIdle()

        playlistHierarchy.findByName("Full Lifecycle Playlist") shouldBePresent {
            it.audioItems.any { item -> item.id == audioItem.id } shouldBe true
        }

        waveforms.size() shouldBe 1
        waveforms.findById(audioItem.id) shouldBePresent { it shouldBe waveform }
    }
})