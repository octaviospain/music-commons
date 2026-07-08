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

package net.transgressoft.commons.fx.music

import net.transgressoft.commons.fx.music.eventuallyAfterFxEvents
import net.transgressoft.commons.music.audio.virtualFiles
import net.transgressoft.commons.music.testing.ReactiveScopeSerialization
import net.transgressoft.commons.music.testing.registryIsolation
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.arbitrary.next
import org.testfx.api.FxToolkit
import org.testfx.util.WaitForAsyncUtils
import java.lang.ref.WeakReference
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi

/**
 * Verifies the close-lifecycle contracts of [FXMusicLibrary] and its underlying [FXAudioLibrary]:
 * idempotency, use-after-close rejection, no stale items across create-close-create cycles, and
 * no retained instance after close.
 */
@ExperimentalCoroutinesApi
internal class FXAudioLibraryLifecycleTest : StringSpec({

    registryIsolation()
    // This spec exercises the real debounce/refresh timing (create-close cycles, WeakReference leak
    // detection) with eventuallyAfterFxEvents, so it keeps ReactiveScope's production dispatchers via
    // ReactiveScopeSerialization rather than swapping in a TestDispatcher.
    extension(ReactiveScopeSerialization)
    val files = virtualFiles()

    beforeSpec {
        FxToolkit.registerPrimaryStage()
    }

    afterSpec {
        FxToolkit.cleanupStages()
    }

    "FXMusicLibrary close() is idempotent" {
        val library = FXMusicLibrary.builder().metadataIO(files.metadataIO).build()
        shouldNotThrowAny {
            library.close()
            library.close()
        }
    }

    "FXAudioLibrary throws IllegalStateException on mutation after close" {
        val library = FXMusicLibrary.builder().metadataIO(files.metadataIO).build()
        library.close()

        shouldThrow<IllegalStateException> {
            library.audioItemFromFile(files.virtualAudioFile().next())
        }
        shouldThrow<IllegalStateException> {
            library.createPlaylistDirectory("after-close")
        }
        // Accessing an import service after close must fail fast rather than construct a new,
        // never-cancelled service scope against the closed library.
        shouldThrow<IllegalStateException> {
            library.itunesImport
        }
    }

    "100 create-close-create cycles leave no stale items in a fresh library" {
        repeat(100) {
            val lib = FXMusicLibrary.builder().metadataIO(files.metadataIO).build()
            lib.audioItemFromFile(files.virtualAudioFile().next())
            eventuallyAfterFxEvents(500.milliseconds) {
                lib.audioItemsProperty.isEmpty() shouldBe false
            }
            lib.close()
        }

        val fresh = FXMusicLibrary.builder().metadataIO(files.metadataIO).build()
        eventuallyAfterFxEvents(200.milliseconds) {
            fresh.audioItemsProperty.isEmpty() shouldBe true
        }
        fresh.close()
    }

    "FXAudioLibrary is not retained after close" {
        var library: FXMusicLibrary? = FXMusicLibrary.builder().metadataIO(files.metadataIO).build()
        val ref = WeakReference(library!!.audioLibrary())

        library.close()
        WaitForAsyncUtils.waitForFxEvents()
        library = null

        eventually(5.seconds) {
            WaitForAsyncUtils.waitForFxEvents()
            System.gc()
            ref.get() shouldBe null
        }
    }
})