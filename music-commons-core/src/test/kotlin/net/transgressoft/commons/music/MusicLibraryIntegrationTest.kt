package net.transgressoft.commons.music

import net.transgressoft.commons.music.audio.*
import net.transgressoft.commons.music.audio.AudioItemTestUtil.mp3File
import net.transgressoft.commons.music.playlist.AudioPlaylistJsonRepository
import net.transgressoft.commons.music.playlist.AudioPlaylistRepository
import net.transgressoft.commons.music.playlist.MutableAudioPlaylist
import net.transgressoft.commons.music.waveform.AudioWaveformJsonRepository
import net.transgressoft.commons.music.waveform.AudioWaveformRepository
import net.transgressoft.commons.music.waveform.ScalableAudioWaveform
import io.kotest.assertions.timing.eventually
import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.spec.tempfile
import io.kotest.matchers.optional.shouldBePresent
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import kotlin.time.Duration.Companion.seconds

internal class MusicLibraryIntegrationTest : StringSpec({

    val audioRepoFile = tempfile("audioItemRepository-test", ".json").apply { deleteOnExit() }
    val playlistRepoFile = tempfile("playlistRepository-test", ".json").apply { deleteOnExit() }
    val waveformsRepoFile = tempfile("waveformRepository-test", ".json").apply { deleteOnExit() }

    val audioItemRepository: AudioItemRepository<MutableAudioItem> = AudioItemJsonRepository("AudioItems", audioRepoFile)
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

        eventually(1.seconds) {
            audioRepoFile.readText() shouldContain audioItem.path.toString()
            audioItemRepository.artistCatalogRegistry.findFirst(audioItem.artist) shouldBePresent { it.containsAudioItem(audioItem) shouldBe true }
        }

        val waveform = audioWaveformRepository.getOrCreateWaveformAsync(audioItem, 780, 335)
        eventually(3.seconds) {
            waveform.get() shouldNotBe null
            waveform.get().id shouldBe audioItem.id
            waveformsRepoFile.readText() shouldContain audioItem.path.toString()
            waveformsRepoFile.readText() shouldContain waveform.get().id.toString()
        }

        audioPlaylistRepository.createPlaylist("Test Playlist").also { it.addAudioItem(audioItem) }

        eventually(2.seconds) {
            playlistRepoFile.readText() shouldContain "Test Playlist"
            playlistRepoFile.readText() shouldContain audioItem.id.toString()
        }

        audioItem.title = "New title"

        eventually(2.seconds) {
            audioItemRepository.contains { it.title == "New title" }
            audioItemRepository.size() shouldBe 1
            audioItemRepository.artistCatalogRegistry.findFirst(audioItem.artist) shouldBePresent { it.containsAudioItem(audioItem) shouldBe true }

            audioRepoFile.readText() shouldContain "New title"
            val updatedPlaylist = audioPlaylistRepository.findByName("Test Playlist").get()
            updatedPlaylist.audioItems.contains(audioItem) shouldBe true
        }

        audioItemRepository.remove(audioItem) shouldBe true
        audioItemRepository.isEmpty shouldBe true

        eventually(2.seconds) {
            audioItemRepository.artistCatalogRegistry.isEmpty shouldBe true
            audioItemRepository.artistCatalogRegistry.findFirst(audioItem.artist).isEmpty shouldBe true

            audioRepoFile.readText() shouldBe "{\n}"

            audioPlaylistRepository.findByName("Test Playlist") shouldBePresent { it.audioItems.isEmpty() shouldBe true }
            playlistRepoFile.readText() shouldBe """
            {
                "1": {
                    "id": 1,
                    "isDirectory": false,
                    "name": "Test Playlist",
                    "audioItemIds": [
                    ],
                    "playlistIds": [
                    ]
                }
            }
            """.trimIndent()

            audioWaveformRepository.isEmpty shouldBe true
            waveformsRepoFile.readText() shouldBe "{\n}"
        }
    }
})