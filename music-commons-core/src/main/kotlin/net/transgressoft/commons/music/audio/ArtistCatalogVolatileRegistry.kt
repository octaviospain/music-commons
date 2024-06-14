package net.transgressoft.commons.music.audio

import net.transgressoft.commons.data.RegistryBase
import net.transgressoft.commons.data.StandardDataEvent.Type.*
import mu.KotlinLogging
import java.util.*
import java.util.stream.Collectors.*

internal class ArtistCatalogVolatileRegistry(override val name: String = "ArtistCatalog") : RegistryBase<String, MutableArtistCatalog>() {

    private val log = KotlinLogging.logger {}

    init {
        disableEvents(CREATE, UPDATE, DELETE)
    }

    fun addAudioItems(audioItems: Collection<AudioItem>): Boolean {
        val catalogsBeforeUpdate = mutableListOf<MutableArtistCatalog>()

        val addedOrReplacedCatalogs: Map<Boolean, List<MutableArtistCatalog>> =
            audioItems.stream().filter { audioItem -> entitiesById.any { it.value.containsAudioItem(audioItem) }.not() }
                .map { audioItem ->
                    entitiesById.merge(audioItem.artistUniqueId(), MutableArtistCatalog(audioItem)) { artistCatalog, _ ->
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

    fun updateCatalog(updatedAudioItem: AudioItem, oldAudioItem: AudioItem) {
        if (artistOrAlbumChanged(updatedAudioItem, oldAudioItem)) {
            val removed = removeAudioItems(listOf(oldAudioItem))
            val added = addAudioItems(listOf(updatedAudioItem))
            check(removed && added) { "Update of an audio item in the catalog is supposed to happen at this point" }

            log.debug { "Artist catalog of ${updatedAudioItem.artist.name} was updated as a result of updating $updatedAudioItem" }
        } else if (audioItemOrderingChanged(updatedAudioItem, oldAudioItem)) {
            val artistCatalog = entitiesById[updatedAudioItem.artistUniqueId()] ?: error("Artist catalog for ${updatedAudioItem.artistUniqueId()} should exist already at this point")
            val artistCatalogBeforeUpdate = artistCatalog.copy()
            artistCatalog.mergeAudioItem(updatedAudioItem)
            putUpdateEvent(listOf(artistCatalog), listOf(artistCatalogBeforeUpdate))
        }
    }

    private fun artistOrAlbumChanged(updatedAudioItem: AudioItem, oldAudioItem: AudioItem): Boolean {
        val artistChanged = updatedAudioItem.artist != oldAudioItem.artist
        val albumChanged = updatedAudioItem.album != oldAudioItem.album
        return artistChanged || albumChanged
    }

    private fun audioItemOrderingChanged(updatedAudioItem: AudioItem, oldAudioItem: AudioItem): Boolean {
        val trackNumberChanged = updatedAudioItem.trackNumber != oldAudioItem.trackNumber
        val discNumberChanged = updatedAudioItem.discNumber != oldAudioItem.discNumber
        return trackNumberChanged || discNumberChanged
    }

    fun removeAudioItems(audioItems: Collection<AudioItem>): Boolean {
        val removedCatalogs = mutableListOf<MutableArtistCatalog>()
        val catalogsBeforeUpdate = mutableListOf<MutableArtistCatalog>()
        val updatedCatalogs = mutableListOf<MutableArtistCatalog>()

        audioItems.forEach { audioItem ->
            entitiesById[audioItem.artistUniqueId()]?.let {
                val oldArtistCatalog = it.copy()
                val wasRemoved = it.removeAudioItem(audioItem)
                if (wasRemoved) {
                    if (it.isEmpty) {
                        removedCatalogs.add(it)
                        entitiesById.remove(audioItem.artistUniqueId())
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

    private fun Collection<MutableArtistCatalog>.toArtistNames(): List<String> = map { it.artist.name }

    private fun AudioItem.artistUniqueId() = ImmutableArtist.id(artist.name, artist.countryCode)

    override fun entityClone(entity: MutableArtistCatalog) = entity.copy()

    fun findFirst(artist: Artist): Optional<MutableArtistCatalog> =
        Optional.ofNullable(entitiesById[artist.id()])

    fun findFirst(artistName: String): Optional<MutableArtistCatalog> =
        Optional.ofNullable(entitiesById.entries.firstOrNull { it.key.lowercase().contains(artistName.lowercase()) }?.value)

    fun findAlbumAudioItems(artist: Artist, albumName: String): Set<AudioItem> =
        entitiesById[artist.id()]?.findAlbumAudioItems(albumName) ?: emptySet()

    override val isEmpty = entitiesById.isEmpty()

    override fun toString() = "ArtistCatalogRegistry(numberOfArtists=${entitiesById.size})"
}
