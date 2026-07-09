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

package net.transgressoft.commons.music.audio

import net.transgressoft.commons.media.persistence.waveform.AudioWaveformMapSerializer
import net.transgressoft.commons.music.CoreMusicLibrary
import net.transgressoft.commons.music.shouldReferenceItemId
import net.transgressoft.commons.music.testing.buildTwoCoreLibrariesWithoutClosing
import net.transgressoft.commons.music.testing.reactiveScope
import net.transgressoft.commons.music.testing.registryIsolation
import net.transgressoft.commons.persistence.music.audio.AudioItemMapSerializer
import net.transgressoft.commons.persistence.music.playlist.AudioPlaylistMapSerializer
import net.transgressoft.lirp.persistence.json.JsonFileRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.annotation.DisplayName
import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.spec.tempfile
import io.kotest.matchers.optional.shouldBePresent
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.property.arbitrary.next
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asExecutor

/**
 * Exercises the cross-library operation matrix under the single-live-instance contract (Option B):
 * two libraries are never live simultaneously; "cross-library" means foreign-object insertion or
 * sequential lifecycle, not two concurrent live instances.
 *
 * The three axes covered are:
 *
 * - **Baseline row:** constructing a second live library while one is still live throws
 *   [IllegalStateException], both via direct construction and via loading a JSON repository
 *   into a new library while the first is alive.
 * - **Foreign-object axis:** an entity created by library A, held after A is closed, is re-homed
 *   into library B on [AudioLibrary.add], triggering the WARN + re-wire behavior and making B's
 *   catalog reflect the item.
 * - **Sequential-lifecycle axis:** building A, populating audio items, a playlist, and a waveform,
 *   closing A, then building B against the same backing repositories yields the same entity ids and
 *   the same playlist and waveform associations — no silent corruption.
 */
@DisplayName("CrossLibraryMatrixTest")
@ExperimentalCoroutinesApi
internal class CrossLibraryMatrixTest : StringSpec({

    registryIsolation()
    val reactive = reactiveScope()
    val files = virtualFiles()

    // --- Baseline row ---

    "CoreMusicLibrary baseline — second live construction throws IllegalStateException" {
        val libraries = buildTwoCoreLibrariesWithoutClosing(files.metadataIO)
        try {
            shouldThrow<IllegalStateException> {
                libraries.attemptSecondConstruction()
            }
        } finally {
            libraries.close()
        }
    }

    "CoreMusicLibrary baseline — loading the same JSON repository while library A is live throws IllegalStateException" {
        val audioFile = tempfile("crossLibrary-baseline-audio", ".json").also { it.deleteOnExit() }
        val playlistsFile = tempfile("crossLibrary-baseline-playlists", ".json").also { it.deleteOnExit() }
        val waveformsFile = tempfile("crossLibrary-baseline-waveforms", ".json").also { it.deleteOnExit() }

        val libraryA =
            CoreMusicLibrary.builder()
                .metadataIO(files.metadataIO)
                .audioRepository(JsonFileRepository(audioFile, AudioItemMapSerializer))
                .playlistRepository(JsonFileRepository(playlistsFile, AudioPlaylistMapSerializer))
                .waveformRepository(JsonFileRepository(waveformsFile, AudioWaveformMapSerializer))
                .build()

        try {
            shouldThrow<IllegalStateException> {
                // A second live library — even wired to the same files — must be rejected because the
                // registry slot is already occupied by libraryA.
                CoreMusicLibrary.builder()
                    .metadataIO(files.metadataIO)
                    .audioRepository(JsonFileRepository(audioFile, AudioItemMapSerializer))
                    .playlistRepository(JsonFileRepository(playlistsFile, AudioPlaylistMapSerializer))
                    .waveformRepository(JsonFileRepository(waveformsFile, AudioWaveformMapSerializer))
                    .build()
            }
        } finally {
            libraryA.close()
        }
    }

    // --- Foreign-object axis ---

    "CoreMusicLibrary foreign-object — item created in A is re-homed into B after A is closed" {
        val libraryA = CoreMusicLibrary.builder().metadataIO(files.metadataIO).build()
        val itemA: AudioItem = libraryA.audioItemFromFile(files.virtualAudioFile().next())
        reactive.advance()

        // Close A; item object reference is retained.
        val originalUniqueId = itemA.uniqueId
        libraryA.close()

        // Build B against a fresh (volatile) repository — sequential, not concurrent with A.
        val libraryB = CoreMusicLibrary.builder().metadataIO(files.metadataIO).build()
        try {
            // Adding A-origin item to B must re-wire the item's metadata IO to B's without error.
            libraryB.audioLibrary().add(itemA)
            reactive.advance()

            // The uniqueId is stable across the re-home — same physical track, same identity key.
            libraryB.audioLibrary().findByUniqueId(originalUniqueId).shouldBePresent { found ->
                found.uniqueId shouldBe originalUniqueId
            }

            // B's library contains exactly the one re-homed item.
            libraryB.audioLibrary().size() shouldBe 1
        } finally {
            libraryB.close()
        }
    }

    // --- Sequential-lifecycle axis ---

    "CoreMusicLibrary sequential-lifecycle — items, playlist, and waveform round-trip through close-reopen with identity contract" {
        val audioFile = tempfile("crossLibrary-seq-audio", ".json").also { it.deleteOnExit() }
        val playlistsFile = tempfile("crossLibrary-seq-playlists", ".json").also { it.deleteOnExit() }
        val waveformsFile = tempfile("crossLibrary-seq-waveforms", ".json").also { it.deleteOnExit() }

        // Build A and populate it.
        val libraryA =
            CoreMusicLibrary.builder()
                .metadataIO(files.metadataIO)
                .audioRepository(JsonFileRepository(audioFile, AudioItemMapSerializer))
                .playlistRepository(JsonFileRepository(playlistsFile, AudioPlaylistMapSerializer))
                .waveformRepository(JsonFileRepository(waveformsFile, AudioWaveformMapSerializer))
                .build()

        val item1 = libraryA.audioItemFromFile(files.virtualAudioFile().next())
        val item2 = libraryA.audioItemFromFile(files.virtualAudioFile().next())
        reactive.advance()

        val playlist = libraryA.createPlaylist("Sequential Playlist")
        playlist.addAudioItem(item1)
        reactive.advance()

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
            CoreMusicLibrary.builder()
                .metadataIO(files.metadataIO)
                .audioRepository(JsonFileRepository(audioFile, AudioItemMapSerializer))
                .playlistRepository(JsonFileRepository(playlistsFile, AudioPlaylistMapSerializer))
                .waveformRepository(JsonFileRepository(waveformsFile, AudioWaveformMapSerializer))
                .build()
        reactive.advance()

        try {
            // Items resolve with the same ids and stable uniqueIds.
            // The path changes from jimfs to the default filesystem on deserialization, but the
            // physical-identity key (fileName-duration-bitRate) remains the same because only the
            // path segment names (title, artist, album) participate, not the filesystem URI scheme.
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

            // Playlist round-trips with same id and still references item1 by id.
            libraryB.playlistHierarchy().findByName("Sequential Playlist").shouldBePresent { restoredPlaylist ->
                restoredPlaylist.id shouldBe playlistId
                // Verify the playlist still holds a reference to item1's id without materializing
                // the item (the path changes from jimfs to default fs on deserialization).
                restoredPlaylist shouldReferenceItemId item1Id
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