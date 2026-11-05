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

import net.transgressoft.commons.music.audio.virtualFiles
import net.transgressoft.commons.music.testing.reactiveScope
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
import java.util.concurrent.Callable
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher

/**
 * Verifies the close-lifecycle contracts of [FXMusicLibrary] and its underlying [FXAudioLibrary]:
 * idempotency, use-after-close rejection, no stale items across create-close-create cycles, and
 * no retained instance after close.
 */
@ExperimentalCoroutinesApi
internal class FXAudioLibraryLifecycleTest : StringSpec({

    registryIsolation()
    val reactive = reactiveScope(StandardTestDispatcher())
    val files = virtualFiles()

    // Reactive callbacks post FX work, which can publish another debounced reactive update.
    // Advance both boundaries without making lifecycle correctness depend on runner speed.
    fun settleReactiveAndFx() {
        repeat(2) {
            reactive.advance()
            WaitForAsyncUtils.asyncFx(Runnable {}).get(2, TimeUnit.SECONDS)
        }
    }

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
            FXMusicLibrary.builder().metadataIO(files.metadataIO).build().use { lib ->
                settleReactiveAndFx()
                lib.audioItemFromFile(files.virtualAudioFile().next())
                settleReactiveAndFx()
                WaitForAsyncUtils.asyncFx(Callable { lib.audioItemsProperty.isEmpty() }).get(2, TimeUnit.SECONDS) shouldBe false
            }
            settleReactiveAndFx()
        }

        FXMusicLibrary.builder().metadataIO(files.metadataIO).build().use { fresh ->
            settleReactiveAndFx()
            WaitForAsyncUtils.asyncFx(Callable { fresh.audioItemsProperty.isEmpty() }).get(2, TimeUnit.SECONDS) shouldBe true
        }
        settleReactiveAndFx()
    }

    "FXMusicLibrary releases its registry slot when a lifecycle assertion fails" {
        shouldThrow<AssertionError> {
            FXMusicLibrary.builder().metadataIO(files.metadataIO).build().use {
                settleReactiveAndFx()
                throw AssertionError("Intentional lifecycle assertion failure")
            }
        }
        settleReactiveAndFx()
        FXMusicLibrary.builder().metadataIO(files.metadataIO).build().use { fresh ->
            settleReactiveAndFx()
            WaitForAsyncUtils.asyncFx(Callable { fresh.audioItemsProperty.isEmpty() }).get(2, TimeUnit.SECONDS) shouldBe true
        }
        settleReactiveAndFx()
    }

    "FXAudioLibrary is not retained after close" {
        var library: FXMusicLibrary? = FXMusicLibrary.builder().metadataIO(files.metadataIO).build()
        val ref = WeakReference(library!!.audioLibrary())

        library.close()
        settleReactiveAndFx()
        library = null

        eventually(5.seconds) {
            System.gc()
            ref.get() shouldBe null
        }
    }
})