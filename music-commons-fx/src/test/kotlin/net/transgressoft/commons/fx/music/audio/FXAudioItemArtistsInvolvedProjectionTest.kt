/******************************************************************************
 * Copyright (C) 2026  Octavio Calleya Garcia                                 *
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

package net.transgressoft.commons.fx.music.audio

import net.transgressoft.commons.music.audio.AlbumDetails
import net.transgressoft.commons.music.audio.Artist
import net.transgressoft.commons.music.audio.AudioItemMetadata
import net.transgressoft.commons.music.testing.ReactiveScopeSerialization
import net.transgressoft.lirp.event.CrudEvent
import net.transgressoft.lirp.event.ReactiveScope
import net.transgressoft.lirp.event.StandardCrudEvent
import net.transgressoft.lirp.persistence.VolatileRepository
import net.transgressoft.lirp.persistence.projection.MultiKeyRegistryProjection
import io.kotest.assertions.withClue
import io.kotest.core.annotation.DisplayName
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import java.nio.file.Path
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Forces a title mutation between the real projection capturing an involved-artist iterator and
 * consuming it. An in-place modification of that set kills the projection's coroutine collector;
 * indexing two successive title-only guests verifies both safe iteration and continued re-keying.
 *
 * Latches establish the interleaving without timing assumptions. The temporary real-dispatcher
 * scope captures collector failures and is installed only while holding [ReactiveScopeSerialization].
 */
@DisplayName("FXAudioItem keeps its artist projection processing updates across concurrent title mutations")
internal class FXAudioItemArtistsInvolvedProjectionTest : StringSpec({

    extension(ReactiveScopeSerialization)

    "a title mutation during CREATE iteration preserves guest buckets and later UPDATE processing" {
        val iteratorCaptured = CountDownLatch(1)
        val resumeIteration = CountDownLatch(1)
        val firstGuestIndexed = CountDownLatch(1)
        val secondGuestIndexed = CountDownLatch(1)
        val failures = ConcurrentLinkedQueue<Throwable>()
        val scopeJob = SupervisorJob()
        val handler =
            CoroutineExceptionHandler { _, throwable ->
                failures.add(throwable)
                // Fail promptly with the collector's cause instead of waiting for an impossible bucket.
                firstGuestIndexed.countDown()
                secondGuestIndexed.countDown()
            }
        val scope = CoroutineScope(Dispatchers.Default + scopeJob + handler)
        val previousFlowScope = ReactiveScope.flowScope
        ReactiveScope.flowScope = scope

        try {
            val primary = Artist.of("Primary")
            val albumArtist = Artist.of("Various Artists")
            val firstGuest = Artist.of("First Guest")
            val secondGuest = Artist.of("Second Guest")
            val metadata = AudioItemMetadata(title = "Song", artist = primary, album = AlbumDetails("Compilation", albumArtist))

            FXAudioItem(Path.of("/projection-race.mp3"), 1, metadata).use { item ->
                VolatileRepository<Int, ObservableAudioItem>().use { repository ->
                    repository.activateEvents(CrudEvent.Type.UPDATE)
                    val gateNextExtraction = AtomicBoolean(true)
                    val projection =
                        MultiKeyRegistryProjection(repository, { audioItem: ObservableAudioItem ->
                            val artists = audioItem.artistsInvolved
                            if (!gateNextExtraction.compareAndSet(true, false)) artists else {
                                // Retain the actual iterator: copying here would conceal the live-set race.
                                object : AbstractCollection<Artist>() {
                                    override val size: Int get() = artists.size

                                    override fun iterator(): Iterator<Artist> {
                                        val iterator = artists.iterator()
                                        iteratorCaptured.countDown()
                                        check(resumeIteration.await(5, TimeUnit.SECONDS)) { "Title mutation did not release CREATE iteration" }
                                        return iterator
                                    }
                                }
                            }
                        })

                    projection.use {
                        projection.addOnChangeListener { buckets ->
                            if (buckets[firstGuest]?.any { it.id == item.id } == true) firstGuestIndexed.countDown()
                            if (buckets[secondGuest]?.any { it.id == item.id } == true) secondGuestIndexed.countDown()
                        }.use {
                            // Initialize against an empty repository so only CREATE enters the gate.
                            projection.size shouldBe 0
                            repository.add(item)
                            withClue("CREATE must capture its artist iterator before the title changes") {
                                iteratorCaptured.await(5, TimeUnit.SECONDS) shouldBe true
                            }

                            item.title = "Song feat First Guest"
                            resumeIteration.countDown()
                            // The projection must re-extract keys even when old/new entity references are identical.
                            repository.emitAsync(StandardCrudEvent.Update(item, item))

                            withClue("First guest UPDATE must survive CREATE iteration; collector failures: ${failures.toList()}") {
                                firstGuestIndexed.await(5, TimeUnit.SECONDS) shouldBe true
                                failures.toList().shouldBeEmpty()
                                projection[firstGuest]?.map { it.id } shouldBe listOf(item.id)
                            }

                            item.title = "Song feat Second Guest"
                            repository.emitAsync(StandardCrudEvent.Update(item, item))

                            withClue("A distinct later guest must be indexed by the same live collector; failures: ${failures.toList()}") {
                                secondGuestIndexed.await(5, TimeUnit.SECONDS) shouldBe true
                                failures.toList().shouldBeEmpty()
                                projection[secondGuest]?.map { it.id } shouldBe listOf(item.id)
                                projection[firstGuest] shouldBe null
                                projection[primary]?.map { it.id } shouldBe listOf(item.id)
                                projection[albumArtist]?.map { it.id } shouldBe listOf(item.id)
                            }
                        }
                    }
                }
            }
        } finally {
            resumeIteration.countDown()
            try {
                withContext(NonCancellable) {
                    withTimeout(5.seconds) { scopeJob.cancelAndJoin() }
                }
            } finally {
                ReactiveScope.flowScope = previousFlowScope
            }
        }
    }
})