package net.transgressoft.commons.music

import net.transgressoft.commons.media.waveform.ScalableAudioWaveform
import net.transgressoft.commons.media.waveform.audioWaveformRepository
import net.transgressoft.commons.music.audio.AlbumDetails
import net.transgressoft.commons.music.audio.Artist
import net.transgressoft.commons.music.audio.AudioItem
import net.transgressoft.commons.music.audio.DefaultAudioLibrary
import net.transgressoft.commons.music.audio.MutableAudioItem
import net.transgressoft.commons.music.audio.event.AudioItemEventSubscriber
import net.transgressoft.commons.music.audio.virtualFiles
import net.transgressoft.commons.music.playlist.DefaultPlaylistHierarchy
import net.transgressoft.commons.music.testing.reactiveScope
import net.transgressoft.commons.music.testing.registryIsolation
import net.transgressoft.commons.music.waveform.AudioWaveform
import net.transgressoft.commons.music.waveform.AudioWaveformRepository
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.optional.shouldBePresent
import io.kotest.matchers.shouldBe
import io.kotest.property.arbitrary.next
import kotlinx.coroutines.ExperimentalCoroutinesApi

/**
 * Integration tests verifying that [AutoCloseable.close] stops event propagation
 * across audio libraries, playlist hierarchies, and waveform repositories.
 */
@ExperimentalCoroutinesApi
internal class LifecycleIntegrationTest : StringSpec({

    registryIsolation()
    val reactive = reactiveScope()
    val files = virtualFiles()

    lateinit var repos: JsonRepoTriad

    lateinit var audioLibrary: DefaultAudioLibrary
    lateinit var waveforms: AudioWaveformRepository<AudioWaveform, AudioItem>
    lateinit var playlistHierarchy: DefaultPlaylistHierarchy

    beforeEach {
        repos = jsonRepoTriad("lifecycle")

        audioLibrary = DefaultAudioLibrary(repos.audioRepository, files.metadataIO)
        val subscriber = AudioItemEventSubscriber<AudioItem>("LifecycleTestSubscriber")
        waveforms = audioWaveformRepository(repos.waveformRepository, subscriber) { subscriber.cancelSubscription() }
        playlistHierarchy = DefaultPlaylistHierarchy(repos.playlistRepository)
    }

    afterEach {
        audioLibrary.close()
        waveforms.close()
        playlistHierarchy.close()
        repos.closeAll()
    }

    "AudioLibrary close() stops reacting to repository events" {
        val artist = Artist.of("Joy Division")
        val album = AlbumDetails("Unknown Pleasures", artist)
        val audioItem =
            audioLibrary.createFromFile(
                files.virtualAudioFile {
                    this.artist = artist
                    this.album = album
                    title = "She's Lost Control"
                }.next()
            )
        reactive.advance()

        // Catalog key is Artist derived from name (no country code)
        audioLibrary.findAlbumAudioItems(artist, album.name).size shouldBe 1

        audioLibrary.close()

        // After close(), add directly to the repository to bypass the AudioLibraryBase guard
        // and verify that the catalog subscription no longer processes the new item.
        val audioFile2 = files.virtualAudioFile().next()
        val audioItem2 = MutableAudioItem(audioFile2, audioLibrary.size() + 1, files.metadataIO.readMetadata(audioFile2))
        repos.audioRepository.add(audioItem2)
        reactive.advance()

        // After close(), the artist catalog registry subscription is cancelled
        // so newly added items are not indexed in the catalog
        audioLibrary shouldNotIndex audioItem2
        audioLibrary.size() shouldBe 2
    }

    "PlaylistHierarchy close() stops reacting to audio item deletions" {
        audioLibrary.subscribe(playlistHierarchy)

        val audioItem = audioLibrary.createFromFile(files.virtualAudioFile().next())
        reactive.advance()

        val playlist = playlistHierarchy.createPlaylist("Lifecycle Test Playlist")
        playlist.addAudioItem(audioItem)
        reactive.advance()

        playlist.audioItems.size shouldBe 1

        playlistHierarchy.close()

        audioLibrary.remove(audioItem) shouldBe true
        reactive.advance()

        // After close(), the audio item deletion event is no longer processed —
        // the playlist's audioItemIds still contains the id even though the audio item was removed from the library.
        // Query through the underlying repository directly to avoid the use-after-close guard on findByName.
        repos.playlistRepository.findFirst { it.name == "Lifecycle Test Playlist" } shouldBePresent {
            it shouldReferenceItemId audioItem.id
        }
    }

    "DefaultAudioWaveformRepository close() cancels its event subscription and closes the delegated repository" {
        audioLibrary.subscribe(waveforms)

        val audioItem = audioLibrary.createFromFile(files.virtualAudioFile().next())
        reactive.advance()

        val waveform = ScalableAudioWaveform(audioItem.id, audioItem.path)
        waveforms.add(waveform) shouldBe true
        reactive.advance()

        waveforms.size() shouldBe 1

        waveforms.close()

        // After close() the underlying repository is closed and the audio item subscription
        // is cancelled, so the DELETE event from audioLibrary.remove(...) must not be
        // delivered to the (now closed) waveforms repo. If the subscription were still
        // active, the subscriber would attempt to mutate a closed publisher and throw.
        shouldNotThrowAny {
            audioLibrary.remove(audioItem) shouldBe true
            reactive.advance()
        }
    }

    "Full lifecycle cleanup closes all components in order" {
        audioLibrary.subscribe(waveforms)
        audioLibrary.subscribe(playlistHierarchy)

        val audioItem = audioLibrary.createFromFile(files.virtualAudioFile().next())
        reactive.advance()

        val playlist = playlistHierarchy.createPlaylist("Full Lifecycle Playlist")
        playlist.addAudioItem(audioItem)
        reactive.advance()

        val waveform = ScalableAudioWaveform(audioItem.id, audioItem.path)
        waveforms.add(waveform) shouldBe true
        reactive.advance()

        // Closing in reverse-dependency order should not throw, even though events may
        // still be in flight between the components.
        shouldNotThrowAny {
            waveforms.close()
            playlistHierarchy.close()
            audioLibrary.close()
        }
    }

    "AudioLibraryBase close() is idempotent — second call is a no-op" {
        shouldNotThrowAny {
            repeat(2) { audioLibrary.close() }
        }
    }

    "AudioLibrary throws IllegalStateException when used after close" {
        audioLibrary.close()
        shouldThrow<IllegalStateException> {
            audioLibrary.createFromFile(files.virtualAudioFile().next())
        }
    }

    "PlaylistHierarchyBase close() is idempotent — second call is a no-op" {
        shouldNotThrowAny {
            repeat(2) { playlistHierarchy.close() }
        }
    }

    "PlaylistHierarchy throws IllegalStateException when used after close" {
        playlistHierarchy.close()
        shouldThrow<IllegalStateException> {
            playlistHierarchy.createPlaylist("Post-close Playlist")
        }
    }
})