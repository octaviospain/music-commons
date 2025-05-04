package net.transgressoft.commons.music.audio

import net.transgressoft.commons.event.CrudEvent.Type.CREATE
import net.transgressoft.commons.event.CrudEvent.Type.DELETE
import net.transgressoft.commons.event.CrudEvent.Type.UPDATE
import net.transgressoft.commons.persistence.RegistryBase
import mu.KotlinLogging
import java.util.Optional
import java.util.stream.Collectors.partitioningBy

internal class ArtistCatalogVolatileRegistry<I>: RegistryBase<String, MutableArtistCatalog<I>>("ArtistCatalog") where I: ReactiveAudioItem<I> {
    private val log = KotlinLogging.logger {}

    init {
        // READ event is disabled by default from upper class
        disableEvents(CREATE, UPDATE, DELETE)
    }

    fun addAudioItems(audioItems: Collection<I>): Boolean {
        synchronized(this) {
            val catalogsBeforeUpdate = mutableListOf<MutableArtistCatalog<I>>()

            val addedOrReplacedCatalogs: Map<Boolean, List<MutableArtistCatalog<I>>> =
                audioItems.stream()
                    .filter { audioItem -> entitiesById.any { it.value.containsAudioItem(audioItem) }.not() }
                    .map { audioItem ->
                        entitiesById.merge(audioItem.artistUniqueId(), MutableArtistCatalog(audioItem)) { artistCatalog, _ ->
                            artistCatalog.addAudioItem(audioItem)
                            artistCatalog.also { catalogsBeforeUpdate.add(it) }
                        }!!
                    }.collect(partitioningBy { it.size == 1 })

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
    }

    fun updateCatalog(updatedAudioItem: I, oldAudioItem: I) {
        synchronized(this) {
            if (artistOrAlbumChanged(updatedAudioItem, oldAudioItem)) {
                val removed = removeAudioItems(listOf(oldAudioItem))
                val added = addAudioItems(listOf(updatedAudioItem))
                check(removed || added) { "Update of an audio item in the catalog is supposed to happen at this point" }

                log.debug { "Artist catalog of ${updatedAudioItem.artist.name} was updated as a result of updating $updatedAudioItem" }
            } else if (audioItemOrderingChanged(updatedAudioItem, oldAudioItem)) {
                val artistCatalog =
                    entitiesById[updatedAudioItem.artistUniqueId()] ?: error(
                        "Artist catalog for ${updatedAudioItem.artistUniqueId()} should exist already at this point"
                    )
                val artistCatalogBeforeUpdate = artistCatalog.copy()
                artistCatalog.mergeAudioItem(updatedAudioItem)
                putUpdateEvent(listOf(artistCatalog), listOf(artistCatalogBeforeUpdate))
            }
        }
    }

    private fun artistOrAlbumChanged(updatedAudioItem: I, oldAudioItem: I): Boolean {
        val artistChanged = updatedAudioItem.artist != oldAudioItem.artist
        val albumChanged = updatedAudioItem.album != oldAudioItem.album
        return artistChanged || albumChanged
    }

    private fun audioItemOrderingChanged(updatedAudioItem: I, oldAudioItem: I): Boolean {
        val trackNumberChanged = updatedAudioItem.trackNumber != oldAudioItem.trackNumber
        val discNumberChanged = updatedAudioItem.discNumber != oldAudioItem.discNumber
        return trackNumberChanged || discNumberChanged
    }

    fun removeAudioItems(audioItems: Collection<I>): Boolean {
        synchronized(this) {
            val removedCatalogs = mutableListOf<MutableArtistCatalog<I>>()
            val catalogsBeforeUpdate = mutableListOf<MutableArtistCatalog<I>>()
            val updatedCatalogs = mutableListOf<MutableArtistCatalog<I>>()

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
    }

    private fun Collection<MutableArtistCatalog<I>>.toArtistNames(): List<String> = map { it.artist.name }

    private fun ReactiveAudioItem<I>.artistUniqueId() = ImmutableArtist.id(artist.name, artist.countryCode)

    fun getArtistView(artist: Artist): Optional<ArtistView<I>> = findFirst(artist).map(MutableArtistCatalog<I>::getArtistView)

    fun findFirst(artist: Artist): Optional<MutableArtistCatalog<I>> = Optional.ofNullable(entitiesById[artist.id()])

    fun findFirst(artistName: String): Optional<MutableArtistCatalog<I>> =
        Optional.ofNullable(entitiesById.entries.firstOrNull { it.key.lowercase().contains(artistName.lowercase()) }?.value)

    fun findAlbumAudioItems(artist: Artist, albumName: String): Set<I> = entitiesById[artist.id()]?.findAlbumAudioItems(albumName) ?: emptySet()

    override val isEmpty = entitiesById.isEmpty()

    override fun toString() = "ArtistCatalogRegistry(numberOfArtists=${entitiesById.size})"
}