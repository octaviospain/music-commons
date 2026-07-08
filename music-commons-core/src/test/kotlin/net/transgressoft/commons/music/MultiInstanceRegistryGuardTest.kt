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

package net.transgressoft.commons.music

import net.transgressoft.commons.music.audio.AudioItem
import net.transgressoft.commons.music.audio.virtualFiles
import net.transgressoft.commons.music.playlist.MutableAudioPlaylist
import net.transgressoft.commons.music.testing.registryIsolation
import net.transgressoft.lirp.persistence.LirpContext
import io.kotest.core.annotation.DisplayName
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

/**
 * Verifies the single-live-instance guard on the Core facade under the two scenarios that require
 * direct inspection of the `LirpContext` registry slot:
 *
 * - **Construct → close → construct-again**: after closing a library the slot is freed, so a fresh
 *   construction must succeed without throwing.
 * - **Concurrent construct/close racing**: many threads race to build and immediately close a library;
 *   the guard ensures that every attempt either wins (and closes cleanly) or is rejected with
 *   `IllegalStateException` — no slot is leaked and no corruption occurs.
 */
@DisplayName("MultiInstanceRegistryGuardTest")
internal class MultiInstanceRegistryGuardTest : StringSpec({

    registryIsolation()
    val files = virtualFiles()

    "a CoreMusicLibrary can be reconstructed after the previous one is closed" {
        val first = CoreMusicLibrary.builder().metadataIO(files.metadataIO).build()
        first.close()

        // Both registry slots must be free after close.
        LirpContext.default.registryFor(AudioItem::class.java) shouldBe null
        LirpContext.default.registryFor(MutableAudioPlaylist::class.java) shouldBe null

        // A second construction must not throw — slot is available.
        val second = CoreMusicLibrary.builder().metadataIO(files.metadataIO).build()
        second.close()

        LirpContext.default.registryFor(AudioItem::class.java) shouldBe null
    }

    "concurrent construct/close on the same entity type yields exactly one live owner and no corruption" {
        val threadCount = 16
        val start = CountDownLatch(1)
        val done = CountDownLatch(threadCount)
        val wins = AtomicInteger(0)
        val rejects = AtomicInteger(0)

        repeat(threadCount) {
            Thread {
                start.await()
                try {
                    val lib = CoreMusicLibrary.builder().metadataIO(files.metadataIO).build()
                    wins.incrementAndGet()
                    lib.close()
                } catch (_: IllegalStateException) {
                    // Expected: loser of the race. May originate from the construction guard or from
                    // the registry's internal atomic check — both are legal outcomes, which is why the
                    // assertions below never rely on an exact exception message.
                    rejects.incrementAndGet()
                } finally {
                    done.countDown()
                }
            }.start()
        }

        start.countDown()
        done.await()

        // Every attempt either won (built and closed cleanly) or was rejected — none corrupted.
        (wins.get() + rejects.get()) shouldBe threadCount
        // At least one thread must have successfully constructed a library — guards against an
        // over-rejection regression where the guard rejects every construction.
        wins.get() shouldNotBe 0
        // After all threads finished, the slot must be free — no leaked owner.
        LirpContext.default.registryFor(AudioItem::class.java) shouldBe null
    }
})