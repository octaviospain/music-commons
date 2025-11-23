/******************************************************************************
 * Copyright (C) 2025  Octavio Calleya Garcia                                 *
 *                                                                            *
 * This program is free software: you can redistribute it and/or modify       *
 * it under the terms of the GNU General Public License as published by       *
 * the Free Software Foundation, either version 3 of the License, or          *
 * (at your option) any later version.                                        *
 *                                                                            *
 * This program is distributed in the hope that it will be useful,            *
 * but WITHOUT ANY WARRANTY; without even the implied warranty of             *
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the              *
 * GNU General Public License for more details.                               *
 *                                                                            *
 * You should have received a copy of the GNU General Public License          *
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.     *
 ******************************************************************************/

package net.transgressoft.commons.music.audio

import net.transgressoft.commons.event.CrudEvent
import net.transgressoft.commons.event.TransEventPublisher
import net.transgressoft.commons.music.player.event.AudioItemPlayerEvent
import net.transgressoft.commons.persistence.Repository
import java.nio.file.Path
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.Flow
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.future.future

/**
 * Represents a library of audio items with repository capabilities and reactive event publishing.
 *
 * This interface extends [Repository] for CRUD operations and [Flow.Publisher] to publish
 * events when audio items are created, updated, or deleted. It also provides artist-centric
 * views through catalogs that organize items by artist and album.
 *
 * @param I The type of audio items stored in this library
 * @param AC The type of artist catalog exposed by this library
 */
interface AudioLibrary<I: ReactiveAudioItem<I>, AC: ReactiveArtistCatalog<AC, I>>: Repository<Int, I>, Flow.Publisher<CrudEvent<Int, I>> {

    /**
     * Subscriber for receiving player events such as when audio items are played.
     */
    val playerSubscriber: Flow.Subscriber<AudioItemPlayerEvent>

    /**
     * Publisher for artist catalog events, enabling consumers to subscribe to changes
     * in artist catalogs (creation, updates, deletion).
     *
     * Consumers can subscribe to be notified when:
     * - A new artist catalog is created (new artist added to an audio library)
     * - An artist catalog is updated (audio items added/removed for an artist)
     * - An artist catalog is deleted (all items for an artist removed from an audio library)
     *
     * The published catalogs are immutable views that update reactively in the background.
     */
    val artistCatalogPublisher: TransEventPublisher<CrudEvent.Type, CrudEvent<Artist, AC>>

    /**
     * Creates an audio item from the file at the specified path.
     *
     * @param audioItemPath Path to the audio file
     * @return The created audio item with populated metadata
     */
    fun createFromFile(audioItemPath: Path): I

    /**
     * Finds all audio items for a specific album by an artist.
     *
     * @param artist The artist to search for
     * @param albumName The name of the album
     * @return Set of audio items matching the artist and album, or empty set if not found
     */
    fun findAlbumAudioItems(artist: Artist, albumName: String): Set<I>

    /**
     * Retrieves the artist catalog for the specified artist.
     *
     * @param artist The artist to get the catalog for
     * @return Optional containing the artist catalog if found, or empty if artist not in library
     */
    fun getArtistCatalog(artist: Artist): Optional<out AC>

    /**
     * Retrieves the first artist catalog matching the specified name.
     *
     * @param artistName The artist name to search for (case-insensitive substring match)
     * @return Optional containing the first matching artist catalog, or empty if not found
     */
    fun getArtistCatalog(artistName: String): Optional<out AC>

    /**
     * Checks whether the library contains any audio item involving the specified artist.
     *
     * @param artistName The artist names to check for (case-insensitive)
     * @return true if at least one audio item involves the artist, false otherwise
     */
    fun containsAudioItemWithArtist(artistName: String): Boolean

    /**
     * Retrieves a random selection of audio items from the specified artist.
     *
     * @param artist The artist to get items from
     * @param size Maximum number of random items to return (default: 100)
     * @return List of randomly selected audio items from the artist
     */
    fun getRandomAudioItemsFromArtist(artist: Artist, size: Short = 100): List<I>

    /**
     * Creates audio items asynchronously from a batch of file paths using the specified dispatcher.
     *
     * The files are processed in batches of 500 for optimal performance.
     */
    fun createFromFileBatchAsync(audioItemPaths: Collection<Path>, dispatcher: CoroutineDispatcher): CompletableFuture<List<I>> {
        val batchSize = 500 // TODO #4 Parameterize this magic constant for customization
        return CoroutineScope(dispatcher).future {
            audioItemPaths.chunked(batchSize).map { batch ->
                async {
                    batch.map { path ->
                        async(dispatcher) { createFromFile(path) }
                    }.awaitAll()
                }
            }.awaitAll().flatten()
        }
    }

    fun createFromFileBatchAsync(audioItemPaths: Collection<Path>, executor: Executor): CompletableFuture<List<I>> =
        createFromFileBatchAsync(audioItemPaths, executor.asCoroutineDispatcher())

    fun createFromFileBatchAsync(audioItemPaths: Collection<Path>): CompletableFuture<List<I>> =
        createFromFileBatchAsync(audioItemPaths, Dispatchers.IO)
}