package net.transgressoft.commons.music

import net.transgressoft.commons.music.audio.AudioItem
import net.transgressoft.commons.music.audio.AudioItemJsonRepository
import net.transgressoft.commons.music.audio.AudioItemRepository
import net.transgressoft.commons.music.audio.AudioItemTestUtil.mp3File
import net.transgressoft.commons.music.playlist.AudioPlaylistJsonRepository
import net.transgressoft.commons.music.playlist.AudioPlaylistRepository
import net.transgressoft.commons.music.playlist.AudioPlaylistTestUtil.asJsonKeyValues
import net.transgressoft.commons.music.playlist.MutableAudioPlaylist
import net.transgressoft.commons.music.waveform.AudioWaveformJsonRepository
import net.transgressoft.commons.music.waveform.AudioWaveformRepository
import net.transgressoft.commons.music.waveform.ScalableAudioWaveform
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.spec.tempfile
import io.kotest.matchers.collections.shouldContainOnly
import io.kotest.matchers.optional.shouldBePresent
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import kotlin.time.Duration.Companion.milliseconds

internal class MusicLibraryIntegrationTest : StringSpec({

    val audioRepoFile = tempfile("audioItemRepository-test", ".json").apply { deleteOnExit() }
    val playlistRepoFile = tempfile("playlistRepository-test", ".json").apply { deleteOnExit() }
    val waveformsRepoFile = tempfile("waveformRepository-test", ".json").apply { deleteOnExit() }

    val audioItemRepository: AudioItemRepository<AudioItem> = AudioItemJsonRepository("AudioItems", audioRepoFile)
    val audioWaveformRepository: AudioWaveformRepository<ScalableAudioWaveform> = AudioWaveformJsonRepository("Waveforms", waveformsRepoFile)
    val audioPlaylistRepository: AudioPlaylistRepository<AudioItem, MutableAudioPlaylist<AudioItem>> = AudioPlaylistJsonRepository("Playlists", playlistRepoFile)

    beforeEach {
        audioItemRepository.clear()
        audioWaveformRepository.clear()
        audioPlaylistRepository.clear()
    }

    "Operations on audio items impact subscribed repositories" {
        audioItemRepository.subscribe(audioWaveformRepository.audioItemEventSubscriber)
        audioItemRepository.subscribe(audioPlaylistRepository.audioItemEventSubscriber)

        val audioItem = audioItemRepository.createFromFile(mp3File.toPath())

        eventually(100.milliseconds) {
            audioRepoFile.readText() shouldContain audioItem.path.toString()
            audioItemRepository.findAlbumAudioItems(audioItem.artist, audioItem.album.name).shouldContainOnly(audioItem)
        }

        val waveform = audioWaveformRepository.getOrCreateWaveformAsync(audioItem, 780, 335)
        eventually(100.milliseconds) {
            waveform.get() shouldNotBe null
            waveform.get().id shouldBe audioItem.id
            waveformsRepoFile.readText() shouldContain audioItem.path.toString()
            waveformsRepoFile.readText() shouldContain waveform.get().id.toString()
        }

        audioPlaylistRepository.createPlaylist("Test Playlist").also { it.addAudioItem(audioItem) }

        eventually(100.milliseconds) {
            playlistRepoFile.readText() shouldContain "Test Playlist"
            playlistRepoFile.readText() shouldContain audioItem.id.toString()
        }

        audioItem.title = "New title"

        eventually(100.milliseconds) {
            audioItemRepository.contains { it.title == "New title" }
            audioItemRepository.size() shouldBe 1
            audioItemRepository.findAlbumAudioItems(audioItem.artist, audioItem.album.name).shouldContainOnly(audioItem)

            audioRepoFile.readText() shouldContain "New title"
            val updatedPlaylist = audioPlaylistRepository.findByName("Test Playlist").get()
            updatedPlaylist.audioItems.contains(audioItem) shouldBe true
        }

        audioItemRepository.remove(audioItem) shouldBe true
        audioItemRepository.isEmpty shouldBe true

        eventually(100.milliseconds) {
            audioItemRepository.findAlbumAudioItems(audioItem.artist, audioItem.album.name).isEmpty() shouldBe true
            audioRepoFile.readText() shouldBe "{}"

            audioPlaylistRepository.findByName("Test Playlist") shouldBePresent {
                it.audioItems.isEmpty() shouldBe true
                playlistRepoFile.readText() shouldEqualJson listOf(it).asJsonKeyValues()
            }

            audioWaveformRepository.isEmpty shouldBe true
            waveformsRepoFile.readText() shouldBe "{}"
        }
    }
})