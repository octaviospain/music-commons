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
import io.kotest.core.annotation.DisplayName
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.arbitrary.next
import org.testfx.api.FxToolkit
import kotlinx.coroutines.ExperimentalCoroutinesApi

/*
 * Confirms the multi-instance registry-overwrite corruption reaches the JavaFX facade, and pins down
 * that its exposure is narrower than Core's. An FX playlist keeps a materialized ObservableList of
 * audio-item objects, so it is IMMUNE when items are added as objects in memory; it only resolves
 * audio ids through the shared registry when a playlist is populated from ids — the same aggregate
 * resolution path exercised on deserialization/load. That id-resolution path is where a second live
 * FXMusicLibrary silently redirects resolution to the wrong repository.
 *
 * No RegistryIsolationExtension, no deregister/register bracketing: the unisolated path is the point.
 * Assertions read the synchronous aggregate (playlistHierarchy().findByName(...).get().audioItems),
 * never the async observable properties.
 */
@DisplayName("MultiInstanceFXCorruptionTest")
@ExperimentalCoroutinesApi
internal class MultiInstanceFXCorruptionTest : StringSpec({

    val files = virtualFiles()

    lateinit var libraries: TwoFXMusicLibraries

    beforeSpec {
        FxToolkit.registerPrimaryStage()
    }

    afterSpec {
        FxToolkit.cleanupStages()
    }

    beforeEach {
        libraries = buildTwoFXLibrariesWithoutClosing(files.metadataIO)
    }

    afterEach {
        libraries.close()
    }

    "second FXMusicLibrary construction silently resolves an id-populated playlist from the wrong repository" {
        // Distinct items in each library; both repositories assign the same repository-local id, so the
        // id-populated playlist in library A resolves (through the overwritten registry) to library B's item.
        val itemInA = libraries.libraryA.audioItemFromFile(files.virtualAudioFile().next())
        libraries.libraryB.audioItemFromFile(files.virtualAudioFile().next())

        // Populate by id — the aggregate resolution path shared with deserialization/load.
        libraries.libraryA.playlistHierarchy().createPlaylist("Library A Playlist", listOf(itemInA.id))

        val resolvedItems =
            libraries.libraryA
                .playlistHierarchy()
                .findByName("Library A Playlist")
                .get()
                .audioItems
        resolvedItems.any { it.uniqueId == itemInA.uniqueId } shouldBe true
    }

    "an FX playlist populated with audio-item objects is immune to registry overwrite" {
        val itemInA = libraries.libraryA.audioItemFromFile(files.virtualAudioFile().next())
        libraries.libraryB.audioItemFromFile(files.virtualAudioFile().next())

        // Object-based population materializes the item directly into the observable cache — no registry re-resolution.
        val playlist = libraries.libraryA.playlistHierarchy().createPlaylist("In-Memory Playlist")
        playlist.addAudioItem(itemInA)

        val resolvedItems =
            libraries.libraryA
                .playlistHierarchy()
                .findByName("In-Memory Playlist")
                .get()
                .audioItems
        resolvedItems.any { it.uniqueId == itemInA.uniqueId } shouldBe true
    }
})