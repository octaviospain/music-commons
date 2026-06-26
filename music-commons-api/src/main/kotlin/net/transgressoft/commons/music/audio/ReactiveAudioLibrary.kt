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

import net.transgressoft.commons.music.player.event.AudioItemPlayerEvent
import net.transgressoft.lirp.event.CrudEvent
import net.transgressoft.lirp.event.LirpEventPublisher
import net.transgressoft.lirp.persistence.Repository
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
 * Generic library of audio items with repository capabilities and reactive event publishing.
 *
 * This interface extends [Repository] for CRUD operations and [Flow.Publisher] to publish
 * events when audio items are created, updated, or deleted. It also provides artist-, album-,
 * and genre-centric views that organize items by their respective key.
 *
 * Narrowed versions for concrete item types are available in the core and FX modules.
 *
 * @param I The type of audio items stored in this library
 * @param AC The type of artist catalog exposed by this library
 * @param ALC The type of reactive album exposed by this library
 * @param GC The type of reactive genre index exposed by this library
 */
interface ReactiveAudioLibrary<
    I : ReactiveAudioItem<I>,
    AC : ReactiveArtistCatalog<AC, I>,
    ALC : ReactiveAlbum<ALC, I>,
    GC : ReactiveGenreIndex<GC, I>
> : Repository<Int, I>, Flow.Publisher<CrudEvent<Int, I>> {

    /**
     * Subscriber for receiving player events such as when audio items are played.
     */
    val playerSubscriber: Flow.Subscriber<AudioItemPlayerEvent>

    /**
     * Creates an audio item via the supplied [factory] and adds it to the library.
     *
     * The library allocates a unique ID internally and passes it to the factory function,
     * keeping ID generation encapsulated. Use this when constructing audio items outside
     * the library's own [createFromFile] path, such as importing from external sources
     * where metadata is provided externally.
     *
     * @param factory function that receives the allocated ID and returns a fully constructed audio item
     * @return the created and registered audio item
     */
    fun createAudioItem(factory: (id: Int) -> I): I

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
    val artistCatalogPublisher: LirpEventPublisher<CrudEvent.Type, CrudEvent<Artist, AC>>

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
     * Publisher for album events, enabling consumers to subscribe to changes
     * in albums (creation, updates, deletion).
     *
     * Consumers can subscribe to be notified when:
     * - A new album is created (a new album is encountered in the library)
     * - An album is updated (audio items added or removed for an album)
     * - An album is deleted (all items for an album removed from the library)
     */
    val albumPublisher: LirpEventPublisher<CrudEvent.Type, CrudEvent<AlbumDetails, ALC>>

    /**
     * Retrieves the album for the specified album details.
     *
     * @param album The album details to look up
     * @return Optional containing the album if found, or empty if the album is not in the library
     */
    fun getAlbum(album: AlbumDetails): Optional<out ALC>

    /**
     * Retrieves the first album whose name contains the specified value (case-insensitive).
     *
     * Albums are identified by their full [AlbumDetails] value, so several distinct albums may share
     * a name (differing in label, year, or album artist). When more than one matches, which album is
     * returned is not deterministic. Use [getAlbum] with the exact [AlbumDetails] to address a
     * specific album unambiguously. A blank name returns empty.
     *
     * @param albumName The album name to search for (case-insensitive substring match)
     * @return Optional containing the first matching album, or empty if not found or the name is blank
     */
    fun getAlbum(albumName: String): Optional<out ALC>

    /**
     * Checks whether the library contains any audio item with the specified album name.
     *
     * @param albumName The album name to check for (case-insensitive)
     * @return true if at least one audio item has a matching album, false otherwise
     */
    fun containsAudioItemWithAlbum(albumName: String): Boolean

    /**
     * Retrieves a random selection of audio items from the specified album.
     *
     * @param album The album details to get items from
     * @param size Maximum number of random items to return (default: 100)
     * @return List of randomly selected audio items from the album
     */
    fun getRandomAudioItemsFromAlbum(album: AlbumDetails, size: Short = 100): List<I>

    /**
     * Publisher for genre index events, enabling consumers to subscribe to changes
     * in genre indexes (creation, updates, deletion).
     *
     * Consumers can subscribe to be notified when:
     * - A new genre index is created (a new genre is encountered in the library)
     * - A genre index is updated (audio items added or removed for a genre)
     * - A genre index is deleted (all items for a genre removed from the library)
     */
    val genreIndexPublisher: LirpEventPublisher<CrudEvent.Type, CrudEvent<Genre, GC>>

    /**
     * Retrieves the genre index for the specified genre.
     *
     * @param genre The genre to get the index for
     * @return Optional containing the genre index if found, or empty if the genre is not in the library
     */
    fun getGenreIndex(genre: Genre): Optional<out GC>

    /**
     * Retrieves the first genre index matching the specified name.
     *
     * @param genreName The genre name to search for (case-insensitive substring match)
     * @return Optional containing the first matching genre index, or empty if not found
     */
    fun getGenreIndex(genreName: String): Optional<out GC>

    /**
     * Checks whether the library contains any audio item with the specified genre name.
     *
     * @param genreName The genre name to check for (case-insensitive)
     * @return true if at least one audio item has a matching genre, false otherwise
     */
    fun containsAudioItemWithGenre(genreName: String): Boolean

    /**
     * Retrieves a random selection of audio items from the specified genre.
     *
     * @param genre The genre to get items from
     * @param size Maximum number of random items to return (default: 100)
     * @return List of randomly selected audio items from the genre
     */
    fun getRandomAudioItemsFromGenre(genre: Genre, size: Short = 100): List<I>

    /**
     * Creates audio items asynchronously from a batch of file paths using the specified dispatcher.
     *
     * Files are processed in parallel batches to balance memory pressure from concurrent file reads
     * against throughput from parallelized I/O. The default batch size of 500 is tuned for typical
     * desktop audio library imports where each file read involves metadata parsing via JAudioTagger.
     *
     * @param audioItemPaths collection of file paths to create audio items from
     * @param dispatcher coroutine dispatcher controlling the parallelism
     * @param batchSize number of files to process per batch (default: 500). Must be positive.
     *   Values below 500 are coerced to 500 to prevent performance degradation.
     * @return a [CompletableFuture] completing with the list of created audio items
     * @throws IllegalArgumentException if [batchSize] is not positive
     */
    fun createFromFileBatchAsync(audioItemPaths: Collection<Path>, dispatcher: CoroutineDispatcher, batchSize: Int = 500): CompletableFuture<List<I>> {
        require(batchSize > 0) { "batchSize must be positive, got $batchSize" }
        val effectiveBatchSize = batchSize.coerceAtLeast(500)
        return CoroutineScope(dispatcher).future {
            buildList(audioItemPaths.size) {
                for (batch in audioItemPaths.chunked(effectiveBatchSize)) {
                    addAll(
                        batch.map { path ->
                            async(dispatcher) { createFromFile(path) }
                        }.awaitAll()
                    )
                }
            }
        }
    }

    /**
     * Creates audio items asynchronously from a batch of file paths using the specified executor.
     *
     * @param audioItemPaths collection of file paths to create audio items from
     * @param executor executor to use for coroutine dispatching
     * @param batchSize number of files to process per batch (default: 500)
     * @return a [CompletableFuture] completing with the list of created audio items
     * @see createFromFileBatchAsync
     */
    fun createFromFileBatchAsync(audioItemPaths: Collection<Path>, executor: Executor, batchSize: Int = 500): CompletableFuture<List<I>> =
        createFromFileBatchAsync(audioItemPaths, executor.asCoroutineDispatcher(), batchSize)

    /**
     * Creates audio items asynchronously from a batch of file paths using [Dispatchers.IO].
     *
     * @param audioItemPaths collection of file paths to create audio items from
     * @param batchSize number of files to process per batch (default: 500)
     * @return a [CompletableFuture] completing with the list of created audio items
     * @see createFromFileBatchAsync
     */
    fun createFromFileBatchAsync(audioItemPaths: Collection<Path>, batchSize: Int = 500): CompletableFuture<List<I>> =
        createFromFileBatchAsync(audioItemPaths, Dispatchers.IO, batchSize)
}