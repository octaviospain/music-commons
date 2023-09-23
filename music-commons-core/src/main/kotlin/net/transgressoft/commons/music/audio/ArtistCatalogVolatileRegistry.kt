package net.transgressoft.commons.music.audio

import net.transgressoft.commons.data.DataEvent
import net.transgressoft.commons.data.RegistryBase
import net.transgressoft.commons.data.StandardDataEvent.Type.*
import net.transgressoft.commons.data.UpdatedDataEvent
import net.transgressoft.commons.event.TransEventSubscriber
import net.transgressoft.commons.music.audio.event.AudioItemEventSubscriber
import mu.KotlinLogging
import java.util.*
import java.util.stream.Collectors.*

internal class ArtistCatalogVolatileRegistry(override val name: String = "ArtistCatalog") :
    ArtistCatalogRegistry,
    RegistryBase<String, ArtistCatalog>(),
    TransEventSubscriber<MutableAudioItem, DataEvent<Int, out MutableAudioItem>> by AudioItemEventSubscriber("$name-AudioItemSubscriber") {

    private val log = KotlinLogging.logger {}

    private val artistCatalogsById: MutableMap<String, MutableArtistCatalog> = HashMap()

    init {
        addOnNextEventAction(CREATE) {
            addAudioItems(it.entitiesById.values)
        }
        addOnNextEventAction(UPDATE) {
            it as UpdatedDataEvent
            it.entitiesById.forEach { (id, updatedAudioItem) ->
                val oldAudioItem = it.oldEntitiesById[id] ?: error("Old audio item not found for updated one with uniqueId ${updatedAudioItem.uniqueId}")
                if (artistOrAlbumChanged(updatedAudioItem, oldAudioItem) || audioItemOrderingChanged(updatedAudioItem, oldAudioItem)) {
                    updateCatalog(updatedAudioItem, oldAudioItem)
                }
            }
        }
        addOnNextEventAction(DELETE) {
            removeAudioItems(it.entitiesById.values)
        }
    }

    private fun addAudioItems(audioItems: Collection<MutableAudioItem>): Boolean {
        val catalogsBeforeUpdate = mutableListOf<ArtistCatalog>()

        val addedOrReplacedCatalogs: Map<Boolean, List<ArtistCatalog>> =
            audioItems.stream().filter { audioItem -> artistCatalogsById.any { it.value.containsAudioItem(audioItem) }.not() }
                .map { audioItem ->
                    artistCatalogsById.merge(audioItem.artistUniqueId(), MutableArtistCatalog(audioItem)) { artistCatalog, _ ->
                        artistCatalog.addAudioItem(audioItem)
                        artistCatalog.also { catalogsBeforeUpdate.add(it) }
                    }!!
                }.collect(partitioningBy { it?.size == 1 })

        addedOrReplacedCatalogs[true]?.let { createdCatalogs ->
            if (createdCatalogs.isNotEmpty()) {
                putCreateEvent(createdCatalogs)
                log.debug { "${createdCatalogs.size} artist catalogs were created" }
            }
        }

        addedOrReplacedCatalogs[false]?.let { updatedCatalogs ->
            if (updatedCatalogs.isNotEmpty()) {
                putUpdateEvent(updatedCatalogs, catalogsBeforeUpdate)
                log.debug { "${updatedCatalogs.size} artist catalogs were updated" }
            }
        }

        return addedOrReplacedCatalogs.isNotEmpty()
    }

    private fun artistOrAlbumChanged(updatedAudioItem: MutableAudioItem, oldAudioItem: MutableAudioItem): Boolean {
        val artistChanged = updatedAudioItem.artist != oldAudioItem.artist
        val albumChanged = updatedAudioItem.album != oldAudioItem.album
        return artistChanged || albumChanged
    }

    private fun audioItemOrderingChanged(updatedAudioItem: MutableAudioItem, oldAudioItem: MutableAudioItem): Boolean {
        val trackNumberChanged = updatedAudioItem.trackNumber != oldAudioItem.trackNumber
        val discNumberChanged = updatedAudioItem.discNumber != oldAudioItem.discNumber
        return trackNumberChanged || discNumberChanged
    }

    private fun updateCatalog(updatedAudioItem: MutableAudioItem, oldAudioItem: MutableAudioItem) {
        if (artistOrAlbumChanged(updatedAudioItem, oldAudioItem)) {
            val removed = removeAudioItems(listOf(oldAudioItem))
            val added = addAudioItems(listOf(updatedAudioItem))
            check(removed && added) { "Update of an audio item in the catalog is supposed to happen at this point" }

            log.debug { "Artist catalog of ${updatedAudioItem.artist.name} was updated as a result of updating $updatedAudioItem" }
        } else if (audioItemOrderingChanged(updatedAudioItem, oldAudioItem)) {
            val artistCatalog = artistCatalogsById[updatedAudioItem.artistUniqueId()] ?: error("Artist catalog for ${updatedAudioItem.artistUniqueId()} should exist already at this point")
            val artistCatalogBeforeUpdate = artistCatalog.copy()
            artistCatalog.mergeAudioItem(updatedAudioItem)
            putUpdateEvent(listOf(artistCatalog), listOf(artistCatalogBeforeUpdate))
        }
    }

    private fun removeAudioItems(audioItems: Collection<MutableAudioItem>): Boolean {
        val removedCatalogs = mutableListOf<ArtistCatalog>()
        val catalogsBeforeUpdate = mutableListOf<ArtistCatalog>()
        val updatedCatalogs = mutableListOf<ArtistCatalog>()

        audioItems.forEach { audioItem ->
            artistCatalogsById[audioItem.artistUniqueId()]?.let {
                val oldArtistCatalog = it.copy()
                val wasRemoved = it.removeAudioItem(audioItem)
                if (wasRemoved) {
                    if (it.isEmpty) {
                        removedCatalogs.add(it)
                        artistCatalogsById.remove(audioItem.artistUniqueId())
                    } else {
                        updatedCatalogs.add(it)
                        catalogsBeforeUpdate.add(oldArtistCatalog)
                    }
                }
            }
        }

        if (removedCatalogs.isNotEmpty()) {
            putDeleteEvent(removedCatalogs)
            log.debug { "Artist catalogs of ${removedCatalogs.toArtistNames()} were deleted as a result of removing $audioItems from them" }
        }

        if (updatedCatalogs.isNotEmpty()) {
            putUpdateEvent(updatedCatalogs, catalogsBeforeUpdate)
            log.debug { "Audio items $audioItems were removed from artist catalogs of ${updatedCatalogs.toArtistNames()}" }
        }

        return removedCatalogs.isNotEmpty() || updatedCatalogs.isNotEmpty()
    }

    private fun Collection<ArtistCatalog>.toArtistNames(): List<String> = map { it.artist.name }

    private fun AudioItem.artistUniqueId() = ImmutableArtist.id(artist.name, artist.countryCode)

    override fun entityClone(entity: ArtistCatalog) = (entity as MutableArtistCatalog).copy()

    override fun findFirst(artistName: String): Optional<ArtistCatalog> =
        Optional.ofNullable(artistCatalogsById.entries.firstOrNull { it.key.lowercase().contains(artistName.lowercase()) }?.value)

    override fun findAlbum(albumName: String, artist: Artist): Optional<Album> =
        artistCatalogsById[artist.id()]?.findAlbum(albumName) ?: Optional.empty()

    override val isEmpty = artistCatalogsById.isEmpty()

    override fun toString() = "ArtistCatalogRegistry(numberOfArtists=${artistCatalogsById.size})"
}