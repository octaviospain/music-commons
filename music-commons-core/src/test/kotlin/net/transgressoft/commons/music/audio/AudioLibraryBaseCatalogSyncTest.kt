package net.transgressoft.commons.music.audio

import net.transgressoft.commons.music.testing.reactiveScope
import net.transgressoft.lirp.event.CrudEvent
import net.transgressoft.lirp.event.CrudEvent.Type.UPDATE
import net.transgressoft.lirp.event.ReactiveMutationEvent
import net.transgressoft.lirp.persistence.VolatileRepository
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.optional.shouldBeEmpty
import io.kotest.matchers.optional.shouldBePresent
import io.kotest.matchers.shouldBe
import io.kotest.property.arbitrary.next
import kotlinx.coroutines.ExperimentalCoroutinesApi

/**
 * Verifies that the artist-catalog re-key subscription in [AudioLibraryBase] is scoped to
 * artist/album property changes (and bulk mutations), and stays silent for non-key mutations
 * such as a title-only change. This guards against the catalog churn that a blanket mutation
 * bridge would cause.
 */
@ExperimentalCoroutinesApi
internal class AudioLibraryBaseCatalogSyncTest : StringSpec({

    val reactive = reactiveScope()
    val files = virtualFiles()
    lateinit var repository: VolatileRepository<Int, AudioItem>
    lateinit var audioLibrary: TestAudioLibrary

    beforeEach {
        repository = VolatileRepository("AudioLibraryBaseCatalogSyncTest")
        audioLibrary = TestAudioLibrary(repository, files.metadataIO)
    }

    afterEach {
        audioLibrary.close()
    }

    "AudioLibraryBase re-keys catalog on artist change" {
        val oldArtist = Artist.of("David Bowie")
        val newArtist = Artist.of("Lou Reed")
        val audioItem =
            audioLibrary.createFromFile(
                files.virtualAudioFile {
                    artist = oldArtist
                    album = Album("Heroes", oldArtist)
                    title = "Heroes"
                }.next()
            )
        reactive.advance()

        audioItem.artist = newArtist
        audioItem.album = Album("Transformer", newArtist)
        reactive.advance()

        audioLibrary.getArtistCatalog(newArtist) shouldBePresent { it.artist.name shouldBe newArtist.name }
        audioLibrary.getArtistCatalog(oldArtist).shouldBeEmpty()
    }

    "AudioLibraryBase emits a catalog re-key on album change" {
        val updateEvents = mutableListOf<CrudEvent<Int, AudioItem>>()
        repository.subscribe(UPDATE) { updateEvents.add(it) }

        val artist = Artist.of("Radiohead")
        val audioItem =
            audioLibrary.createFromFile(
                files.virtualAudioFile {
                    this.artist = artist
                    album = Album("Pablo Honey", artist)
                    title = "Creep"
                }.next()
            )
        reactive.advance()
        updateEvents.clear()

        audioItem.album = Album("OK Computer", artist)
        reactive.advance()

        updateEvents.isEmpty() shouldBe false
    }

    "AudioLibraryBase does not re-key on title-only change" {
        val updateEvents = mutableListOf<CrudEvent<Int, AudioItem>>()
        repository.subscribe(UPDATE) { updateEvents.add(it) }

        val audioItem =
            audioLibrary.createFromFile(
                files.virtualAudioFile {
                    artist = Artist.of("Aphex Twin")
                    album = Album("Selected Ambient Works", Artist.of("Aphex Twin"))
                    title = "Xtal"
                    trackNumber = 1
                }.next()
            )
        reactive.advance()
        updateEvents.clear()

        audioItem.title = "Tha"
        audioItem.bpm = 120f
        audioItem.trackNumber = 5
        reactive.advance()

        updateEvents.isEmpty() shouldBe true
    }

    "AudioLibraryBase re-keys catalog on bulk mutation" {
        val updateEvents = mutableListOf<CrudEvent<Int, AudioItem>>()
        repository.subscribe(UPDATE) { updateEvents.add(it) }

        val audioItem =
            audioLibrary.createFromFile(
                files.virtualAudioFile {
                    artist = Artist.of("Burial")
                    album = Album("Untrue", Artist.of("Burial"))
                    title = "Archangel"
                }.next()
            )
        reactive.advance()
        updateEvents.clear()

        val newArtist = Artist.of("Four Tet")
        audioItem.withEventsDisabled {
            audioItem.artist = newArtist
            audioItem.album = Album("Rounds", newArtist)
            audioItem.title = "She Moves She"
        }
        audioItem.emitAsync(ReactiveMutationEvent(audioItem))
        reactive.advance()

        updateEvents.isEmpty() shouldBe false
        audioLibrary.getArtistCatalog(newArtist) shouldBePresent { it.artist.name shouldBe newArtist.name }
    }
})