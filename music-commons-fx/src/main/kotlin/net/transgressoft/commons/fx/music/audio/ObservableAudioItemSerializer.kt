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

package net.transgressoft.commons.fx.music.audio

import net.transgressoft.commons.music.audio.AudioItemMetadata
import net.transgressoft.commons.music.audio.AudioItemSerializerBase
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Path
import java.time.LocalDateTime
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

/**
 * [KSerializer] for `Map<Int, ObservableAudioItem>` — the JavaFX-typed counterpart of
 * `AudioItemMapSerializer` for use with the FX `MusicLibrary`.
 *
 * Consumers wiring a custom `JsonFileRepository` pass this serializer directly:
 *
 * ```
 * val repository = JsonFileRepository(audioFile, ObservableAudioItemMapSerializer)
 * FXMusicLibrary.builder().audioRepository(repository).build()
 * ```
 *
 * The element serializer is the polymorphic [ObservableAudioItemSerializer], which materializes
 * deserialized entries as [FXAudioItem] instances with their JavaFX property bindings
 * reconstructed. Polymorphic subtypes for `Artist`, `Album`, and `Label` are registered through
 * [observableAudioItemSerializerModule]; pass it as `serializersModule` when constructing a `Json`
 * instance manually. `JsonFileRepository` wires it automatically.
 *
 * Thread-safety: the serializer is stateless; concurrent reads are safe.
 *
 * @see observableAudioItemSerializerModule
 */
@get:JvmName("ObservableAudioItemMapSerializer")
val ObservableAudioItemMapSerializer: KSerializer<Map<Int, ObservableAudioItem>> = MapSerializer(Int.serializer(), ObservableAudioItemSerializer())

/**
 * Kotlinx serialization serializer for [ObservableAudioItem] instances.
 *
 * Serializes JavaFX audio items to JSON, preserving all metadata while excluding
 * transient JavaFX properties. Creates [FXAudioItem] instances during deserialization
 * that automatically reconstruct JavaFX property bindings.
 *
 * @param fileSystem the [FileSystem] used to materialize [Path] instances during
 *  deserialization. Defaults to [FileSystems.getDefault]; tests may pass a Jimfs
 *  filesystem to deserialize against an in-memory tree.
 */
internal class ObservableAudioItemSerializer
    @JvmOverloads
    constructor(
        fileSystem: FileSystem = FileSystems.getDefault()
    ) : AudioItemSerializerBase<ObservableAudioItem>(fileSystem) {

        override fun constructEntity(
            path: Path,
            id: Int,
            metadata: AudioItemMetadata,
            dateOfCreation: LocalDateTime,
            lastDateModified: LocalDateTime,
            playCount: Short
        ): ObservableAudioItem =
            // Cover bytes from JSON are intentionally not seeded here; the FXAudioItem lazy getter
            // loads them through the library back-ref wired by FXAudioLibrary.add on rehydration.
            FXAudioItem(path, id, metadata, dateOfCreation, lastDateModified, playCount)
    }