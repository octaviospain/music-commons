package net.transgressoft.commons.music.audio

import net.transgressoft.commons.data.DataEvent
import net.transgressoft.commons.data.RegistryBase
import net.transgressoft.commons.data.StandardDataEvent.*
import net.transgressoft.commons.data.StandardDataEvent.Type.*
import net.transgressoft.commons.data.UpdatedDataEvent
import net.transgressoft.commons.event.TransEventSubscriber
import net.transgressoft.commons.music.event.AudioItemEventSubscriber
import mu.KotlinLogging
import java.util.*

internal class ArtistCatalogVolatileRegistry(override val name: String = "ArtistCatalog") :
    ArtistCatalogRegistry,
    RegistryBase<String, ArtistCatalog>(),
    TransEventSubscriber<MutableAudioItem, DataEvent<Int, out MutableAudioItem>> by AudioItemEventSubscriber("$name-AudioItemSubscriber") {

    private val log = KotlinLogging.logger {}

    private val artistCatalogsById: MutableMap<String, MutableArtistCatalog> = HashMap()

    init {
        addOnNextEventAction(CREATE) {
            it.entitiesById.values.forEach(::addAudioItem)
        }
        addOnNextEventAction(UPDATE) {
            it as UpdatedDataEvent
            it.entitiesById.keys.forEach { id ->
                val audioItem = it.entitiesById[id]!!
                val oldAudioItem = it.oldEntitiesById[id]!!

                if (audioItem.artistsInvolved != oldAudioItem.artistsInvolved) {

                    val oldArtistCatalog = artistCatalogsById[oldAudioItem.artistUniqueId()]
                }
            }
        }
        addOnNextEventAction(Type.DELETE) {
            it.entitiesById.values.forEach(::removeAudioItem)
        }
    }

    private fun addAudioItem(audioItem: MutableAudioItem) {
        artistCatalogsById.merge(audioItem.artistUniqueId(), MutableArtistCatalog(audioItem).also { putCreateEvent(it) }) { artistCatalog, _ ->
            val oldArtistCatalog = artistCatalog.copy()
            artistCatalog.addAudioItem(audioItem)
            artistCatalog.also {
                putUpdateEvent(it, oldArtistCatalog)
            }
        }
        log.debug { "AudioItem $audioItem was added to artist catalog of ${audioItem.artist}" }
    }

    private fun removeAudioItem(audioItem: MutableAudioItem) {
        artistCatalogsById[audioItem.artistUniqueId()]?.let {
            val oldArtistCatalog = it.copy()
            it.removeAudioItem(audioItem)
            if (it.albums.isEmpty()) {
                putDeleteEvent(it)
                artistCatalogsById.remove(audioItem.artistUniqueId())
                log.debug { "ArtistCatalog of ${audioItem.artist} was removed" }
            } else {
                putUpdateEvent(it, oldArtistCatalog)
                log.debug { "ArtistCatalog of ${audioItem.artist} was updated removing $audioItem" }
            }
        }
    }

    private fun AudioItem.artistUniqueId() = ImmutableArtist.id(artist.name, artist.countryCode)

    override fun entityClone(entity: ArtistCatalog) = (entity as MutableArtistCatalog).copy()

    override fun findFirst(artistName: String): Optional<ArtistCatalog> =
        Optional.ofNullable(artistCatalogsById.entries.firstOrNull { it.key.lowercase().contains(artistName.lowercase()) }?.value)

    override val isEmpty = artistCatalogsById.isEmpty()

    override fun toString() = "ArtistCatalogRegistry(numberOfArtists=${artistCatalogsById.size})"
}