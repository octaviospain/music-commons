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
import net.transgressoft.commons.fx.music.eventuallyAfterFxEvents
import net.transgressoft.commons.music.audio.virtualFiles
import net.transgressoft.commons.music.testing.ReactiveScopeSerialization
import net.transgressoft.commons.music.testing.registryIsolation
import io.kotest.core.annotation.DisplayName
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.arbitrary.next
import org.testfx.api.FxToolkit
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi

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
    extension(ReactiveScopeSerialization)
    val files = virtualFiles()

    beforeSpec {
        FxToolkit.registerPrimaryStage()
    }

    afterSpec {
        FxToolkit.cleanupStages()
    }

    "#13 queued audio-items refresh is a no-op after close: no stale items in a fresh library" {
        // Add an item and then immediately close before the debounced refresh drains.
        // The closed-flag guard (closed.get() inside Platform.runLater) must absorb the pending
        // refresh so the observable collections are never mutated post-close.
        val library = FXMusicLibrary.builder().metadataIO(files.metadataIO).build()
        library.audioItemFromFile(files.virtualAudioFile().next())
        // Close immediately — the debounced refresh may not have fired yet
        library.close()

        // A fresh library must see an empty audioItemsProperty:
        // the closed predecessor's pending runLater must not inject any items into it.
        val fresh = FXMusicLibrary.builder().metadataIO(files.metadataIO).build()
        try {
            eventuallyAfterFxEvents(2.seconds) {
                fresh.audioItemsProperty.isEmpty() shouldBe true
            }
        } finally {
            fresh.close()
        }
    }

    "#13 queued catalog refresh is a no-op after close: no stale catalogs in a fresh library" {
        val library = FXMusicLibrary.builder().metadataIO(files.metadataIO).build()
        library.audioItemFromFile(files.virtualAudioFile().next())
        library.close()

        val fresh = FXMusicLibrary.builder().metadataIO(files.metadataIO).build()
        try {
            eventuallyAfterFxEvents(2.seconds) {
                fresh.artistCatalogsProperty.isEmpty() shouldBe true
            }
        } finally {
            fresh.close()
        }
    }

    "#13 rapid create-close cycles leave no residue in the final library" {
        repeat(5) {
            val lib = FXMusicLibrary.builder().metadataIO(files.metadataIO).build()
            lib.audioItemFromFile(files.virtualAudioFile().next())
            eventuallyAfterFxEvents(500.milliseconds) {
                lib.audioItemsProperty.isEmpty() shouldBe false
            }
            lib.close()
        }

        val last = FXMusicLibrary.builder().metadataIO(files.metadataIO).build()
        try {
            eventuallyAfterFxEvents(2.seconds) {
                last.audioItemsProperty.isEmpty() shouldBe true
            }
        } finally {
            last.close()
        }
    }
})