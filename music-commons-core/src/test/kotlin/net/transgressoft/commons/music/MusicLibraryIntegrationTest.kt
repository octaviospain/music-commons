package net.transgressoft.commons.music

import io.kotest.assertions.timing.eventually
import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.spec.tempfile
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import net.transgressoft.commons.music.audio.*
import net.transgressoft.commons.music.audio.AudioItemTestUtil.mp3File
import net.transgressoft.commons.music.playlist.AudioPlaylist
import net.transgressoft.commons.music.playlist.AudioPlaylistJsonRepository
import net.transgressoft.commons.music.playlist.AudioPlaylistRepository
import net.transgressoft.commons.music.waveform.AudioWaveformJsonRepository
import net.transgressoft.commons.music.waveform.AudioWaveformRepository
import net.transgressoft.commons.music.waveform.ScalableAudioWaveform
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds

internal class MusicLibraryIntegrationTest : StringSpec({

    val audioRepoFile = tempfile("audioItemRepository-test", ".json").apply { deleteOnExit() }
    val playlistRepoFile = tempfile("playlistRepository-test", ".json").apply { deleteOnExit() }
    val waveformsRepoFile = tempfile("waveformRepository-test", ".json").apply { deleteOnExit() }

    val audioItemRepository: AudioItemRepository<AudioItem> = AudioItemJsonRepository(audioRepoFile)
    val audioWaveformRepository: AudioWaveformRepository<ScalableAudioWaveform> = AudioWaveformJsonRepository(waveformsRepoFile)
    val audioPlaylistRepository: AudioPlaylistRepository<AudioItem, AudioPlaylist<AudioItem>> = AudioPlaylistJsonRepository(playlistRepoFile)

    fun AudioItemRepository<AudioItem>.createAudioItem(path: Path): AudioItem =
        ImmutableAudioItem.createFromFile(path).let {
            add(it)
            findByUniqueId(it.uniqueId).get()
        }

    beforeEach {
        audioItemRepository.clear()
        audioWaveformRepository.clear()
        audioPlaylistRepository.clear()
    }

    "Operations from Audio Item repository impact other subscribed repositories" {
        audioItemRepository.subscribe(audioWaveformRepository.audioItemEventSubscriber)
        audioItemRepository.subscribe(audioPlaylistRepository.audioItemEventSubscriber)

        val audioItem = audioItemRepository.createAudioItem(mp3File.toPath())
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

        audioPlaylistRepository.createPlaylist("New ones").let {
            audioPlaylistRepository.addAudioItemsToPlaylist(listOf(audioItem), it)
        }

        eventually(2.seconds) {
            playlistRepoFile.readText() shouldContain "New ones"
            playlistRepoFile.readText() shouldContain audioItem.id.toString()
        }

        audioItemRepository.remove(audioItem) shouldBe true
        audioItemRepository.isEmpty shouldBe true
        audioItemRepository.artists().isEmpty() shouldBe true
        audioItemRepository.artistAlbums(audioItem.artist).isEmpty() shouldBe true
        audioItemRepository.albumAudioItems(audioItem.album).isEmpty() shouldBe true

        eventually(2.seconds) {
            audioRepoFile.readText() shouldBe """
            {
                "repository": "AudioItemRepository"
            }""".trimIndent()

            audioPlaylistRepository.findByName("New ones")!!.audioItems.isEmpty() shouldBe true
            playlistRepoFile.readText() shouldBe """
            [
                {
                    "id": 1,
                    "isDirectory": false,
                    "name": "New ones",
                    "audioItemIds": [
                    ],
                    "playlistIds": [
                    ]
                }
            ]""".trimIndent()

            audioWaveformRepository.isEmpty shouldBe true
            waveformsRepoFile.readText() shouldBe """
            {
            }""".trimIndent()
        }
    }
})