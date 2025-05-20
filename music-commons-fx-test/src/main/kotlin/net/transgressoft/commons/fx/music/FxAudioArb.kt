/******************************************************************************
 *     Copyright (C) 2025  Octavio Calleya Garcia                             *
 *                                                                            *
 *     This program is free software: you can redistribute it and/or modify   *
 *     it under the terms of the GNU General Public License as published by   *
 *     the Free Software Foundation, either version 3 of the License, or      *
 *     (at your option) any later version.                                    *
 *                                                                            *
 *     This program is distributed in the hope that it will be useful,        *
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of         *
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the          *
 *     GNU General Public License for more details.                           *
 *                                                                            *
 *     You should have received a copy of the GNU General Public License      *
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>. *
 ******************************************************************************/

package net.transgressoft.commons.fx.music

import net.transgressoft.commons.fx.music.audio.FXAudioItem
import net.transgressoft.commons.fx.music.audio.ObservableAudioItem
import net.transgressoft.commons.music.audio.AudioItemTestAttributes
import net.transgressoft.commons.music.audio.audioAttributes
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.mockk.every
import io.mockk.mockk
import javafx.beans.property.SimpleObjectProperty
import javafx.scene.image.Image
import java.util.Optional

fun Arb.Companion.fxAudioItem(attributesAction: AudioItemTestAttributes.() -> Unit = {}): Arb<ObservableAudioItem> =
    arbitrary {
        val attributes = audioAttributes().bind()
        attributesAction(attributes)
        fxAudioItem(attributes).bind()
    }

fun Arb.Companion.fxAudioItem(attributes: AudioItemTestAttributes): Arb<ObservableAudioItem> =
    arbitrary {
        mockk<FXAudioItem>(relaxed = true) {
            // immutable properties
            every { id } returns attributes.id
            every { path } returns attributes.path
            every { duration } returns attributes.duration
            every { bitRate } returns attributes.bitRate
            every { encoder } returns attributes.encoder
            every { encoding } returns attributes.encoding
            every { dateOfCreation } returns attributes.dateOfCreation
            every { lastDateModified } returns attributes.lastDateModified
            every { playCount } returns attributes.playCount

            // mutable properties
            every { title } returns attributes.title
            every { artist } returns attributes.artist
            every { album } returns attributes.album
            every { genre } returns attributes.genre
            every { comments } returns attributes.comments
            every { trackNumber } returns attributes.trackNumber
            every { discNumber } returns attributes.discNumber
            every { bpm } returns attributes.bpm
            every { coverImageBytes } returns attributes.coverImageBytes
            every { playCount } returns attributes.playCount

            every { this@mockk.coverImageProperty } answers {
                SimpleObjectProperty(this, "cover image", Optional.empty<Image>())
            }

            every { this@mockk.artistsInvolved } answers { callOriginal() }
            every { this@mockk.asJsonKeyValue() } answers { callOriginal() }
            every { this@mockk.asJsonValue() } answers { callOriginal() }
            every { this@mockk.toString() } answers { callOriginal() }
        }
    }