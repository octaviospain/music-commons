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
 * events when audio items are created, updated, or deleted.
 */
interface AudioLibrary<I: ReactiveAudioItem<I>>: Repository<Int, I>, Flow.Publisher<CrudEvent<Int, I>> {

    val playerSubscriber: Flow.Subscriber<AudioItemPlayerEvent>

    fun createFromFile(audioItemPath: Path): I

    fun findAlbumAudioItems(artist: Artist, albumName: String): Set<I>

    fun getArtistCatalog(artist: Artist): Optional<ArtistView<I>>

    fun containsAudioItemWithArtist(artistName: String): Boolean

    fun getRandomAudioItemsFromArtist(artist: Artist, size: Short = 100): List<I>

    /**
     * Creates audio items asynchronously from a batch of file paths using the specified dispatcher.
     *
     * The files are processed in batches of 500 for optimal performance.
     */
    fun createFromFileBatchAsync(audioItemPaths: Collection<Path>, dispatcher: CoroutineDispatcher): CompletableFuture<List<I>> {
        val batchSize = 500 // TODO Parameterize this magic constant for customization
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