package net.transgressoft.commons.music.audio

import net.transgressoft.commons.event.CrudEvent.Type.CREATE
import net.transgressoft.commons.event.CrudEvent.Type.DELETE
import net.transgressoft.commons.event.CrudEvent.Type.READ
import net.transgressoft.commons.event.CrudEvent.Type.UPDATE
import net.transgressoft.commons.event.EntityChangeEvent
import net.transgressoft.commons.music.event.PlayedEventSubscriber
import net.transgressoft.commons.persistence.Repository
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

abstract class AudioLibraryBase<I : ReactiveAudioItem<I>>(
    protected val repository: Repository<Int, I>
): AudioLibrary<I>, Repository<Int, I> by repository {

    private val artistCatalogRegistry = ArtistCatalogRegistry<I>()

    private val subscription =
        repository.subscribe { event ->
            when (event.type) {
                CREATE -> artistCatalogRegistry.addAudioItems(event.entities.values)
                UPDATE -> {
                    event as EntityChangeEvent<Int, I>
                    event.entities.values.forEach { updatedAudioItem ->
                        val oldEntity =
                            event.oldEntities.values.firstOrNull { old -> old.id == updatedAudioItem.id }
                                ?: error("Old entity not found for updated one with id ${updatedAudioItem.id}")
                        artistCatalogRegistry.updateCatalog(updatedAudioItem, oldEntity)
                    }
                }

                DELETE -> artistCatalogRegistry.removeAudioItems(event.entities.values)
                READ -> Unit // Nothing to do here
            }
        }

    private val idCounter = AtomicInteger(1)

    protected fun newId(): Int {
        var id: Int
        do {
            id = idCounter.getAndIncrement()
        } while (contains(id))
        return id
    }

    override val playerSubscriber = PlayedEventSubscriber()

    override fun findAlbumAudioItems(artist: Artist, albumName: String): Set<I> = artistCatalogRegistry.findAlbumAudioItems(artist, albumName)

    override fun getArtistCatalog(artist: Artist): Optional<ArtistView<I>> = artistCatalogRegistry.getArtistView(artist)

    override fun containsAudioItemWithArtist(artistName: String) =
        repository.contains {
            it.artistsInvolved.any { artist ->
                artist.name.contentEquals(artistName, true)
            }
        }

    override fun getRandomAudioItemsFromArtist(artist: Artist, size: Short): List<I> =
        repository.search(2) { it.artist == artist }.shuffled().toList()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val that = other as AudioLibraryBase<*>
        return artistCatalogRegistry == that.artistCatalogRegistry
    }

    override fun hashCode() = Objects.hash(artistCatalogRegistry)
}