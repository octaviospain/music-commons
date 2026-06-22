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

package net.transgressoft.commons.persistence.fx.music.audio

import net.transgressoft.commons.fx.music.audio.ObservableAudioItem
import net.transgressoft.commons.music.audio.AudioItemMetadata
import net.transgressoft.commons.persistence.music.audio.audioItemSerializersModule
import net.transgressoft.commons.persistence.music.lirpSerializerFor
import net.transgressoft.commons.persistence.music.rawConstruct
import net.transgressoft.lirp.entity.ReactiveEntityBase
import java.nio.file.Path
import java.time.LocalDateTime
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

/**
 * [KSerializer] for `Map<Int, ObservableAudioItem>` used to round-trip a JavaFX audio library
 * through JSON.
 *
 * Consumers wiring a custom `JsonFileRepository` pass this serializer directly:
 *
 * ```
 * val repository = JsonFileRepository(audioFile, ObservableAudioItemMapSerializer)
 * FXMusicLibrary.builder().audioRepository(repository).build()
 * ```
 *
 * The element serializer is lirp's reflective `lirpSerializer(sample, module)`, where the module is
 * the core-tier [audioItemSerializersModule] — the JavaFX audio item nests the same non-`@Serializable`
 * value types as the core audio item (artist, album, label, genre, metadata), so the same contextual
 * serializers resolve every nested field without annotating the domain. The sample instance is built
 * construction-free through the audio item's co-located `_LirpRawConstructor` (resolved reflectively),
 * so this serializer touches no internal constructor and adds no factory.
 *
 * Thread-safety: the serializer is stateless; concurrent reads are safe.
 */
@get:JvmName("ObservableAudioItemMapSerializer")
val ObservableAudioItemMapSerializer: KSerializer<Map<Int, ObservableAudioItem>> =
    MapSerializer(Int.serializer(), lirpSerializerFor(sampleObservableAudioItem(), audioItemSerializersModule))

private fun sampleObservableAudioItem(): ReactiveEntityBase<Int, ObservableAudioItem> =
    rawConstruct(
        "net.transgressoft.commons.fx.music.audio.FXAudioItem",
        mapOf(
            "path" to Path.of("sample"),
            "id" to 0,
            "metadata" to AudioItemMetadata(),
            "dateOfCreation" to LocalDateTime.of(2000, 1, 1, 0, 0, 0),
            "lastDateModified" to LocalDateTime.of(2000, 1, 1, 0, 0, 0),
            "playCount" to 0.toShort()
        )
    )