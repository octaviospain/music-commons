package net.transgressoft.commons.music

import io.kotest.assertions.timing.eventually
import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.spec.tempfile
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import net.transgressoft.commons.music.audio.*
import net.transgressoft.commons.music.audio.AudioItemTestUtil.mp3File
import net.transgressoft.commons.music.playlist.AudioPlaylist
import net.transgressoft.commons.music.playlist.AudioPlaylistInMemoryRepository
import net.transgressoft.commons.music.playlist.AudioPlaylistRepository
import net.transgressoft.commons.music.waveform.AudioWaveformInMemoryRepository
import net.transgressoft.commons.music.waveform.AudioWaveformRepository
import net.transgressoft.commons.music.waveform.ScalableAudioWaveform
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds

internal class MusicLibraryIntegrationTest : StringSpec({

    val repositoryJsonFile = tempfile("audioItemRepository-test", ".json").apply { deleteOnExit() }

    val audioItemRepository: AudioItemRepository<AudioItemBase> = AudioItemJsonRepository.initialize(repositoryJsonFile)
    val audioWaveformRepository: AudioWaveformRepository<ScalableAudioWaveform> = AudioWaveformInMemoryRepository()
    val audioPlaylistRepository: AudioPlaylistRepository<AudioItem, AudioPlaylist<AudioItem>> = AudioPlaylistInMemoryRepository()

    fun AudioItemRepository<AudioItemBase>.createAudioItem(path: Path): AudioItemBase =
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
            repositoryJsonFile.readText() shouldContain audioItem.path.toString()
        }

        val waveform = audioWaveformRepository.getOrCreateWaveformAsync(audioItem, 780, 335)
        eventually(3.seconds) {
            waveform.get() shouldNotBe null
            waveform.get().id shouldBe audioItem.id
        }

        audioPlaylistRepository.createPlaylist("New ones").let {
            audioPlaylistRepository.addAudioItemsToPlaylist(listOf(audioItem), it)
        }

        audioItemRepository.remove(audioItem) shouldBe true
        audioItemRepository.isEmpty shouldBe true
        audioItemRepository.artists().isEmpty() shouldBe true
        audioItemRepository.artistAlbums(audioItem.artist).isEmpty() shouldBe true
        audioItemRepository.albumAudioItems(audioItem.album).isEmpty() shouldBe true

        eventually(2.seconds) {
            audioWaveformRepository.isEmpty shouldBe true
            audioPlaylistRepository.findByName("New ones")?.audioItems?.isEmpty() shouldBe true
            repositoryJsonFile.readText() shouldNotContain audioItem.path.toString()
            repositoryJsonFile.readText() shouldNotContain audioItem.artist.name
            repositoryJsonFile.readText() shouldBe """
            {
                "repository": "AudioItemRepository",
                "jsonFile": "${repositoryJsonFile.absolutePath}"
            }""".trimIndent()
        }
    }
})