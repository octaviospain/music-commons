package net.transgressoft.commons.music.audio

import net.transgressoft.commons.event.CrudEvent
import net.transgressoft.commons.music.player.event.AudioItemPlayerEvent
import net.transgressoft.commons.persistence.Repository
import java.io.Closeable
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

interface AudioItemRepository<I: ReactiveAudioItem<I>>: Repository<Int, I>, Flow.Publisher<CrudEvent<Int, I>>, Closeable {

    val playerSubscriber: Flow.Subscriber<AudioItemPlayerEvent>

    fun createFromFile(audioItemPath: Path): I

    fun findAlbumAudioItems(artist: Artist, albumName: String): Set<I>

    fun getArtistCatalog(artist: Artist): Optional<ArtistView<I>>

    fun containsAudioItemWithArtist(artistName: String): Boolean

    fun getRandomAudioItemsFromArtist(artist: Artist, size: Short = 100): List<I>

    fun createFromFileBatchAsync(audioItemPaths: Collection<Path>, dispatcher: CoroutineDispatcher): CompletableFuture<List<I>> {
        val batchSize = 500
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