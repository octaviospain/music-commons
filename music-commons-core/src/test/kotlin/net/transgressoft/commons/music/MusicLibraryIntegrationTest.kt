package net.transgressoft.commons.music

import io.kotest.assertions.timing.eventually
import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.spec.tempfile
import io.kotest.matchers.optional.shouldBePresent
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import net.transgressoft.commons.music.audio.AudioItem
import net.transgressoft.commons.music.audio.AudioItemJsonRepository
import net.transgressoft.commons.music.audio.AudioItemRepository
import net.transgressoft.commons.music.audio.AudioItemTestUtil.mp3File
import net.transgressoft.commons.music.audio.ImmutableAudioItem
import net.transgressoft.commons.music.playlist.AudioPlaylistJsonRepository
import net.transgressoft.commons.music.playlist.AudioPlaylistRepository
import net.transgressoft.commons.music.playlist.MutableAudioPlaylist
import net.transgressoft.commons.music.waveform.AudioWaveformJsonRepository
import net.transgressoft.commons.music.waveform.AudioWaveformRepository
import net.transgressoft.commons.music.waveform.ScalableAudioWaveform
import kotlin.time.Duration.Companion.seconds

internal class MusicLibraryIntegrationTest : StringSpec({

    val audioRepoFile = tempfile("audioItemRepository-test", ".json").apply { deleteOnExit() }
    val playlistRepoFile = tempfile("playlistRepository-test", ".json").apply { deleteOnExit() }
    val waveformsRepoFile = tempfile("waveformRepository-test", ".json").apply { deleteOnExit() }

    val audioItemRepository: AudioItemRepository<ImmutableAudioItem> = AudioItemJsonRepository(audioRepoFile)
    val audioWaveformRepository: AudioWaveformRepository<ScalableAudioWaveform> = AudioWaveformJsonRepository(waveformsRepoFile)
    val audioPlaylistRepository: AudioPlaylistRepository<AudioItem, MutableAudioPlaylist<AudioItem>> = AudioPlaylistJsonRepository(playlistRepoFile)

    beforeEach {
        audioItemRepository.clear()
        audioWaveformRepository.clear()
        audioPlaylistRepository.clear()
    }

    "Operations on audio items impact subscribed repositories" {
        audioItemRepository.subscribe(audioWaveformRepository.audioItemEventSubscriber)
        audioItemRepository.subscribe(audioPlaylistRepository.audioItemEventSubscriber)

        val audioItem = ImmutableAudioItem.createFromFile(mp3File.toPath()).let {
            audioItemRepository.add(it)
            audioItemRepository.findByUniqueId(it.uniqueId).get()
        }
        eventually(1.seconds) {
            audioRepoFile.readText() shouldContain audioItem.path.toString()
        }

        val waveform = audioWaveformRepository.getOrCreateWaveformAsync(audioItem, 780, 335)
        eventually(3.seconds) {
            waveform.get() shouldNotBe null
            waveform.get().id shouldBe audioItem.id
            waveformsRepoFile.readText() shouldContain audioItem.path.toString()
            waveformsRepoFile.readText() shouldContain waveform.get().id.toString()
        }

        audioPlaylistRepository.createPlaylist("New ones").also { it.addAudioItem(audioItem) }

        eventually(2.seconds) {
            playlistRepoFile.readText() shouldContain "New ones"
            playlistRepoFile.readText() shouldContain audioItem.id.toString()
        }

        audioItemRepository.addOrReplace(audioItem.update { title = "New title" })
        val updatedAudioItem = audioItemRepository.findById(audioItem.id).get()

        eventually(2.seconds) {
            audioItemRepository.contains { it.title == "New title" }
            audioItemRepository.size() shouldBe 1
            audioRepoFile.readText() shouldContain "New title"
            val updatedPlaylist = audioPlaylistRepository.findByName("New ones").get()
            updatedPlaylist.audioItems.contains(updatedAudioItem) shouldBe true
        }

        audioItemRepository.remove(audioItem) shouldBe true
        audioItemRepository.isEmpty shouldBe true
        audioItemRepository.artists().isEmpty() shouldBe true
        audioItemRepository.artistAlbums(audioItem.artist).isEmpty() shouldBe true
        audioItemRepository.albumAudioItems(audioItem.album).isEmpty() shouldBe true

        eventually(2.seconds) {
            audioRepoFile.readText() shouldBe "{\n}"

            audioPlaylistRepository.findByName("New ones") shouldBePresent { it.audioItems.isEmpty() shouldBe true }
            playlistRepoFile.readText() shouldBe """
            {
                "1": {
                    "id": 1,
                    "isDirectory": false,
                    "name": "New ones",
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