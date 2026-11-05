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

import net.transgressoft.commons.fx.music.FXMusicLibrary
import net.transgressoft.commons.music.audio.virtualFiles
import net.transgressoft.commons.music.testing.reactiveScope
import net.transgressoft.commons.music.testing.registryIsolation
import io.kotest.core.annotation.DisplayName
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.arbitrary.next
import org.testfx.api.FxToolkit
import org.testfx.util.WaitForAsyncUtils
import java.util.concurrent.Callable
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher

/**
 * Regression test for the closed-flag guard in [FXAudioLibrary]: queued
 * `drainAudioItemChanges` and `refreshCatalogProperties` dispatches are no-ops after
 * [FXMusicLibrary.close]. A freshly-constructed replacement library must expose only its own
 * items — never stale items from a closed predecessor that fired a pending [Platform.runLater]
 * after closure.
 */
@DisplayName("FXAudioLibrary close-refresh regression — queued refresh is no-op after close")
@ExperimentalCoroutinesApi
internal class FXAudioLibraryCloseRefreshTest : StringSpec({

    registryIsolation()
    val reactive = reactiveScope(StandardTestDispatcher())
    val files = virtualFiles()

    // FX callbacks can publish another reactive update; settle both boundaries without sleeps.
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

    "#13 queued audio-items refresh is a no-op after close: no stale items in a fresh library" {
        val library = FXMusicLibrary.builder().metadataIO(files.metadataIO).build()
        library.use {
            settleReactiveAndFx()
            library.audioItemFromFile(files.virtualAudioFile().next())
            // Start the refresh launch but do not advance its debounce before close.
            reactive.dispatcher.scheduler.runCurrent()
            WaitForAsyncUtils.asyncFx(Callable { library.audioItemsProperty.isEmpty() }).get(2, TimeUnit.SECONDS) shouldBe true
        }
        settleReactiveAndFx()
        WaitForAsyncUtils.asyncFx(Callable { library.audioItemsProperty.isEmpty() }).get(2, TimeUnit.SECONDS) shouldBe true

        // A fresh library must see an empty audioItemsProperty:
        // the closed predecessor's pending runLater must not inject any items into it.
        FXMusicLibrary.builder().metadataIO(files.metadataIO).build().use { fresh ->
            settleReactiveAndFx()
            WaitForAsyncUtils.asyncFx(Callable { fresh.audioItemsProperty.isEmpty() }).get(2, TimeUnit.SECONDS) shouldBe true
        }
        settleReactiveAndFx()
    }

    "#13 queued catalog refresh is a no-op after close: no stale catalogs in a fresh library" {
        val library = FXMusicLibrary.builder().metadataIO(files.metadataIO).build()
        library.use {
            settleReactiveAndFx()
            library.audioItemFromFile(files.virtualAudioFile().next())
            reactive.dispatcher.scheduler.runCurrent()
            // Let the FX factory publish its catalog, then start (but do not advance) its debounce.
            WaitForAsyncUtils.asyncFx(Runnable {}).get(2, TimeUnit.SECONDS)
            reactive.dispatcher.scheduler.runCurrent()
            WaitForAsyncUtils.asyncFx(Callable { library.artistCatalogsProperty.isEmpty() }).get(2, TimeUnit.SECONDS) shouldBe true
        }
        settleReactiveAndFx()
        WaitForAsyncUtils.asyncFx(Callable { library.artistCatalogsProperty.isEmpty() }).get(2, TimeUnit.SECONDS) shouldBe true

        FXMusicLibrary.builder().metadataIO(files.metadataIO).build().use { fresh ->
            settleReactiveAndFx()
            WaitForAsyncUtils.asyncFx(Callable { fresh.artistCatalogsProperty.isEmpty() }).get(2, TimeUnit.SECONDS) shouldBe true
        }
        settleReactiveAndFx()
    }

    "#13 rapid create-close cycles leave no residue in the final library" {
        repeat(5) {
            FXMusicLibrary.builder().metadataIO(files.metadataIO).build().use { lib ->
                settleReactiveAndFx()
                lib.audioItemFromFile(files.virtualAudioFile().next())
                settleReactiveAndFx()
                WaitForAsyncUtils.asyncFx(Callable { lib.audioItemsProperty.isEmpty() }).get(2, TimeUnit.SECONDS) shouldBe false
            }
            settleReactiveAndFx()
        }

        FXMusicLibrary.builder().metadataIO(files.metadataIO).build().use { last ->
            settleReactiveAndFx()
            WaitForAsyncUtils.asyncFx(Callable { last.audioItemsProperty.isEmpty() }).get(2, TimeUnit.SECONDS) shouldBe true
        }
        settleReactiveAndFx()
    }
})