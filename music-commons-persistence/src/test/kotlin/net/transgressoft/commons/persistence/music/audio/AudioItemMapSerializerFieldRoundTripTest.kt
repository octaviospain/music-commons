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

package net.transgressoft.commons.persistence.music.audio

import net.transgressoft.commons.music.CoreMusicLibrary
import net.transgressoft.commons.music.audio.ArbitraryAudioFile.realAudioFile
import net.transgressoft.commons.music.audio.AudioFileTagType.ID3_V_24
import net.transgressoft.commons.music.testing.assertOptionalFieldRoundTrips
import net.transgressoft.commons.music.testing.reactiveScope
import net.transgressoft.lirp.persistence.json.JsonFileRepository
import io.kotest.core.annotation.DisplayName
import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.spec.tempfile
import io.kotest.property.Arb
import io.kotest.property.arbitrary.next

@DisplayName("AudioItemMapSerializer")
internal class AudioItemMapSerializerFieldRoundTripTest : StringSpec({

    val reactive = reactiveScope()

    "AudioItemMapSerializer round-trips an optional field present and absent" {
        val audioFile = tempfile("audioLibrary-optional-field", ".json").apply { deleteOnExit() }

        val library =
            CoreMusicLibrary.builder()
                .audioRepository(JsonFileRepository(audioFile, AudioItemMapSerializer))
                .build()

        val item = library.audioItemFromFile(Arb.realAudioFile(ID3_V_24).next())
        // Ensure the optional comments field is populated so it appears in the wire JSON.
        item.comments = "round-trip comment"
        reactive.advance()

        library.close()

        val reloaded =
            CoreMusicLibrary.builder()
                .audioRepository(JsonFileRepository(audioFile, AudioItemMapSerializer))
                .build()

        val presentMap = mapOf(item.id to reloaded.audioLibrary().findById(item.id).get())
        reloaded.close()

        // Exercises field-present (comments in wire JSON) and field-absent: lirp's reflective
        // serializer fails fast when a reactive-property field is stripped from persisted JSON.
        assertOptionalFieldRoundTrips(AudioItemMapSerializer, presentMap, "comments")
    }
})