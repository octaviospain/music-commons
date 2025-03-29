package net.transgressoft.commons.music

import net.transgressoft.commons.music.audio.AudioItem
import net.transgressoft.commons.music.audio.AudioItemJsonRepository
import net.transgressoft.commons.music.audio.AudioItemTestUtil.mp3File
import net.transgressoft.commons.music.audio.AudioRepository
import net.transgressoft.commons.music.playlist.AudioPlaylistJsonRepository
import net.transgressoft.commons.music.playlist.AudioPlaylistTestUtil.asJsonKeyValues
import net.transgressoft.commons.music.playlist.PlaylistRepository
import net.transgressoft.commons.music.waveform.AudioWaveformJsonRepository
import net.transgressoft.commons.music.waveform.WaveformRepository
import net.transgressoft.commons.persistence.ReactiveScope
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.spec.tempfile
import io.kotest.matchers.collections.shouldContainOnly
import io.kotest.matchers.optional.shouldBePresent
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.test.UnconfinedTestDispatcher

@ExperimentalCoroutinesApi
internal class MusicLibraryIntegrationTest : StringSpec({

    val testDispatcher = UnconfinedTestDispatcher()
    val testScope = CoroutineScope(testDispatcher)

    val audioRepoFile = tempfile("audioItemRepository-test", ".json").apply { deleteOnExit() }
    val playlistRepoFile = tempfile("playlistRepository-test", ".json").apply { deleteOnExit() }
    val waveformsRepoFile = tempfile("waveformRepository-test", ".json").apply { deleteOnExit() }

    lateinit var audioItemRepository: AudioRepository
    lateinit var audioWaveformRepository: WaveformRepository<AudioItem>
    lateinit var audioPlaylistRepository: PlaylistRepository

    beforeSpec {
        ReactiveScope.setDefaultFlowScope(testScope)
        ReactiveScope.setDefaultIoScope(testScope)
    }

    beforeEach {
        audioItemRepository = AudioItemJsonRepository("AudioItems", audioRepoFile)
        audioWaveformRepository = AudioWaveformJsonRepository("Waveforms", waveformsRepoFile)
        audioPlaylistRepository = AudioPlaylistJsonRepository("Playlists", playlistRepoFile)
    }

    afterEach {
        audioItemRepository.close()
        audioWaveformRepository.close()
        audioPlaylistRepository.close()
    }

    afterSpec {
        ReactiveScope.setDefaultFlowScope(CoroutineScope(Dispatchers.Default.limitedParallelism(4) + SupervisorJob()))
        ReactiveScope.setDefaultIoScope(CoroutineScope(Dispatchers.IO.limitedParallelism(1) + SupervisorJob()))
    }

    "Operations on audio items impact subscribed repositories" {
        audioItemRepository.subscribe(audioWaveformRepository.audioItemEventSubscriber)
        audioItemRepository.subscribe(audioPlaylistRepository.audioItemEventSubscriber)

        val audioItem = audioItemRepository.createFromFile(mp3File.toPath())

        testDispatcher.scheduler.advanceUntilIdle()

        audioRepoFile.readText() shouldContain audioItem.path.toString()
        audioItemRepository.findAlbumAudioItems(audioItem.artist, audioItem.album.name).shouldContainOnly(audioItem)

        val waveform = audioWaveformRepository.getOrCreateWaveformAsync(audioItem, 780, 335, testDispatcher.asExecutor())

        testDispatcher.scheduler.advanceUntilIdle()

        waveform.get() shouldNotBe null
        waveform.get().id shouldBe audioItem.id
        waveformsRepoFile.readText() shouldContain audioItem.path.toString()
        waveformsRepoFile.readText() shouldContain waveform.get().id.toString()

        audioPlaylistRepository.createPlaylist("Test Playlist").also { it.addAudioItem(audioItem) }

        testDispatcher.scheduler.advanceUntilIdle()

        playlistRepoFile.readText() shouldContain "Test Playlist"
        playlistRepoFile.readText() shouldContain audioItem.id.toString()

        audioItem.title = "New title"
        testDispatcher.scheduler.advanceUntilIdle()

        audioItemRepository.contains { it.title == "New title" }
        audioItemRepository.size() shouldBe 1
        audioItemRepository.findAlbumAudioItems(audioItem.artist, audioItem.album.name).shouldContainOnly(audioItem)

        audioRepoFile.readText() shouldContain "New title"
        val updatedPlaylist = audioPlaylistRepository.findByName("Test Playlist").get()
        updatedPlaylist.audioItems.contains(audioItem) shouldBe true

        audioItemRepository.remove(audioItem) shouldBe true
        audioItemRepository.isEmpty shouldBe true

        testDispatcher.scheduler.advanceUntilIdle()

        audioItemRepository.findAlbumAudioItems(audioItem.artist, audioItem.album.name).isEmpty() shouldBe true
        audioRepoFile.readText() shouldBe "{}"

        audioPlaylistRepository.findByName("Test Playlist") shouldBePresent {
            it.audioItems.isEmpty() shouldBe true
            playlistRepoFile.readText() shouldEqualJson listOf(it).asJsonKeyValues()
        }

        audioWaveformRepository.isEmpty shouldBe true
        waveformsRepoFile.readText() shouldBe "{}"
    }
})