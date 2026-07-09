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
import net.transgressoft.commons.fx.music.TwoFXMusicLibraries
import net.transgressoft.commons.fx.music.audio.ObservableAudioItem
import net.transgressoft.commons.fx.music.buildTwoFXLibrariesWithoutClosing
import net.transgressoft.commons.fx.music.eventuallyAfterFxEvents
import net.transgressoft.commons.media.persistence.waveform.AudioWaveformMapSerializer
import net.transgressoft.commons.music.audio.virtualFiles
import net.transgressoft.commons.music.testing.reactiveScope
import net.transgressoft.commons.music.testing.registryIsolation
import net.transgressoft.commons.persistence.fx.music.audio.ObservableAudioItemMapSerializer
import net.transgressoft.commons.persistence.fx.music.playlist.ObservablePlaylistMapSerializer
import net.transgressoft.lirp.persistence.json.JsonFileRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.annotation.DisplayName
import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.spec.tempfile
import io.kotest.matchers.optional.shouldBePresent
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.property.arbitrary.next
import org.testfx.api.FxToolkit
import org.testfx.util.WaitForAsyncUtils
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asExecutor

/**
 * JavaFX mirror of the Core cross-library matrix: exercises the same three axes (baseline,
 * foreign-object, sequential-lifecycle) on [FXMusicLibrary] with [eventuallyAfterFxEvents]
 * settling for observable-property assertions.
 *
 * All three axes are covered under the single-live-instance contract (Option B):
 *
 * - **Baseline row:** constructing a second live [FXMusicLibrary] while one is still live throws
 *   [IllegalStateException], both via direct construction and via loading a JSON repository into
 *   a new library while the first is alive.
 * - **Foreign-object axis:** unlike Core's [MutableAudioItem] (which supports re-homing), an
 *   [ObservableAudioItem]'s FX scalar delegates are single-bound at creation and cannot be re-bound
 *   to a second library's registry. Attempting to add an A-origin FX item to B while B is live
 *   throws [IllegalStateException]; B's library remains empty.
 * - **Sequential-lifecycle axis:** building A, populating audio items, a playlist, and a waveform,
 *   closing A, then building B against the same JSON repositories yields the same entity ids and the
 *   same playlist and waveform associations — no silent corruption on the FX facade.
 */
@DisplayName("CrossLibraryMatrixFXTest")
@ExperimentalCoroutinesApi
internal class CrossLibraryMatrixFXTest : StringSpec({

    registryIsolation()
    val reactive = reactiveScope()
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

    // --- Baseline row ---

    "FXMusicLibrary baseline — second live construction throws IllegalStateException" {
        shouldThrow<IllegalStateException> {
            libraries.attemptSecondConstruction()
        }
    }

    "FXMusicLibrary baseline — loading the same JSON repository while library A is live throws IllegalStateException" {
        val audioFile = tempfile("crossFxBaseline-audio", ".json").also { it.deleteOnExit() }
        val playlistsFile = tempfile("crossFxBaseline-playlists", ".json").also { it.deleteOnExit() }
        val waveformsFile = tempfile("crossFxBaseline-waveforms", ".json").also { it.deleteOnExit() }

        // Close the fixture library so this test owns the registry slot exclusively.
        libraries.close()

        val libraryA =
            FXMusicLibrary.builder()
                .metadataIO(files.metadataIO)
                .audioRepository(JsonFileRepository(audioFile, ObservableAudioItemMapSerializer))
                // loadOnInit=false: FXMusicLibrary.Builder.build() calls load() itself for playlist repos
                .playlistRepository(JsonFileRepository(playlistsFile, ObservablePlaylistMapSerializer, loadOnInit = false))
                .waveformRepository(JsonFileRepository(waveformsFile, AudioWaveformMapSerializer))
                .build()

        try {
            shouldThrow<IllegalStateException> {
                // A second live library — even wired to the same files — must be rejected because the
                // registry slot is already occupied by libraryA.
                FXMusicLibrary.builder()
                    .metadataIO(files.metadataIO)
                    .audioRepository(JsonFileRepository(audioFile, ObservableAudioItemMapSerializer))
                    .playlistRepository(JsonFileRepository(playlistsFile, ObservablePlaylistMapSerializer, loadOnInit = false))
                    .waveformRepository(JsonFileRepository(waveformsFile, AudioWaveformMapSerializer))
                    .build()
            }
        } finally {
            libraryA.close()
        }
    }

    // --- Foreign-object axis ---

    "FXMusicLibrary foreign-object — FX item created in A cannot be re-homed into B (FX delegates are single-bound)" {
        // Close the fixture library; this test manages the library lifecycle explicitly.
        libraries.close()

        val libraryA = FXMusicLibrary.builder().metadataIO(files.metadataIO).build()
        val itemA: ObservableAudioItem = libraryA.audioItemFromFile(files.virtualAudioFile().next())
        reactive.advance()
        WaitForAsyncUtils.waitForFxEvents()

        libraryA.close()

        // Unlike Core's MutableAudioItem (which supports re-homing), an ObservableAudioItem's
        // FX scalar delegates are single-bound at creation and cannot be re-bound to a second
        // library's registry. Attempting to add an A-origin FX item to B throws IllegalStateException.
        val libraryB = FXMusicLibrary.builder().metadataIO(files.metadataIO).build()
        try {
            // The FX delegate binding fails and surfaces as IllegalStateException — the re-home
            // is rejected before the item is fully registered.
            shouldThrow<IllegalStateException> {
                libraryB.audioLibrary().add(itemA)
            }
        } finally {
            libraryB.close()
        }
    }

    // --- Sequential-lifecycle axis ---

    "FXMusicLibrary sequential-lifecycle — items, playlist, and waveform round-trip through close-reopen with identity contract" {
        val audioFile = tempfile("crossFxSeq-audio", ".json").also { it.deleteOnExit() }
        val playlistsFile = tempfile("crossFxSeq-playlists", ".json").also { it.deleteOnExit() }
        val waveformsFile = tempfile("crossFxSeq-waveforms", ".json").also { it.deleteOnExit() }

        // Close the fixture library so this test owns the registry slot exclusively.
        libraries.close()

        // Build A and populate it.
        val libraryA =
            FXMusicLibrary.builder()
                .metadataIO(files.metadataIO)
                .audioRepository(JsonFileRepository(audioFile, ObservableAudioItemMapSerializer))
                // loadOnInit=false: FXMusicLibrary.Builder.build() calls load() itself for playlist repos
                .playlistRepository(JsonFileRepository(playlistsFile, ObservablePlaylistMapSerializer, loadOnInit = false))
                .waveformRepository(JsonFileRepository(waveformsFile, AudioWaveformMapSerializer))
                .build()

        val item1 = libraryA.audioItemFromFile(files.virtualAudioFile().next())
        val item2 = libraryA.audioItemFromFile(files.virtualAudioFile().next())
        reactive.advance()
        WaitForAsyncUtils.waitForFxEvents()

        val playlist = libraryA.createPlaylist("FX Sequential Playlist")
        playlist.addAudioItem(item1)
        reactive.advance()
        WaitForAsyncUtils.waitForFxEvents()

        val waveform = libraryA.waveformRepository().getOrCreateWaveformAsync(item1, 780, 335, reactive.dispatcher.asExecutor())
        reactive.advance()
        val waveformId = waveform.get().id
        val waveformAudioFilePath = item1.path.fileName.toString()

        // Capture identity before close.
        val item1Id = item1.id
        val item2Id = item2.id
        val item1UniqueId = item1.uniqueId
        val item2UniqueId = item2.uniqueId
        val playlistId = playlist.id

        libraryA.close()

        // Build B against the same backing stores — sequential construction is permitted.
        val libraryB =
            FXMusicLibrary.builder()
                .metadataIO(files.metadataIO)
                .audioRepository(JsonFileRepository(audioFile, ObservableAudioItemMapSerializer))
                // loadOnInit=false: FXMusicLibrary.Builder.build() calls load() itself for playlist repos
                .playlistRepository(JsonFileRepository(playlistsFile, ObservablePlaylistMapSerializer, loadOnInit = false))
                .waveformRepository(JsonFileRepository(waveformsFile, AudioWaveformMapSerializer))
                .build()
        reactive.advance()
        WaitForAsyncUtils.waitForFxEvents()

        try {
            // Items resolve with the same ids and stable uniqueIds.
            // The path changes from jimfs to the default filesystem on deserialization, but the
            // physical-identity key (fileName-duration-bitRate) remains stable because only the
            // path segment names participate, not the filesystem URI scheme.
            libraryB.audioLibrary().findById(item1Id).shouldBePresent { loaded ->
                loaded.id shouldBe item1Id
                loaded.uniqueId shouldBe item1UniqueId
            }
            libraryB.audioLibrary().findById(item2Id).shouldBePresent { loaded ->
                loaded.id shouldBe item2Id
                loaded.uniqueId shouldBe item2UniqueId
            }

            // Items do NOT resolve to the wrong entity (no cross-resolution corruption).
            libraryB.audioLibrary().findById(item1Id).shouldBePresent { it.uniqueId shouldNotBe item2UniqueId }
            libraryB.audioLibrary().findById(item2Id).shouldBePresent { it.uniqueId shouldNotBe item1UniqueId }

            // The observable property settles with both re-loaded items.
            eventuallyAfterFxEvents(2.seconds) {
                libraryB.audioItemsProperty.size shouldBe 2
            }

            // Playlist round-trips with same id.
            libraryB.findPlaylistByName("FX Sequential Playlist").shouldBePresent { restoredPlaylist ->
                restoredPlaylist.id shouldBe playlistId
            }

            // Waveform round-trips with the same id and its audio-file association is preserved.
            libraryB.waveformRepository().findById(waveformId).shouldBePresent { restoredWaveform ->
                restoredWaveform.id shouldBe waveformId
                // The waveform links back to the audio file by its path's filename — stable across
                // the jimfs-to-default-filesystem deserialization boundary.
                restoredWaveform.audioFilePath.fileName.toString() shouldBe waveformAudioFilePath
            }
        } finally {
            libraryB.close()
        }
    }
})