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

import net.transgressoft.commons.fx.music.audio.FXAudioItem
import net.transgressoft.commons.fx.music.audio.ObservableAudioItem
import net.transgressoft.commons.music.audio.AudioItemTestAttributes
import net.transgressoft.commons.music.audio.audioAttributes
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.mockk.every
import io.mockk.mockk
import javafx.beans.property.SimpleFloatProperty
import javafx.beans.property.SimpleIntegerProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.property.SimpleSetProperty
import javafx.beans.property.SimpleStringProperty
import javafx.collections.FXCollections
import javafx.scene.image.Image
import java.io.ByteArrayInputStream
import java.util.Optional
import kotlin.io.path.extension

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
            every { fileName } returns attributes.path.fileName.toString()
            every { extension } returns attributes.path.extension
            every { length } returns path.toFile().length()

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

            every { this@mockk.dateOfCreationProperty } answers {
                SimpleObjectProperty(this, "date of creation", attributes.dateOfCreation)
            }
            every { this@mockk.artistProperty } answers {
                SimpleObjectProperty(this, "artist", attributes.artist)
            }
            every { this@mockk.albumProperty } answers {
                SimpleObjectProperty(this, "album", attributes.album)
            }
            every { this@mockk.genreProperty } answers {
                SimpleObjectProperty(this, "genre", attributes.genre)
            }
            every { this@mockk.commentsProperty } answers {
                SimpleStringProperty(this, "comments", attributes.comments)
            }
            every { this@mockk.trackNumberProperty } answers {
                SimpleIntegerProperty(this, "track number", attributes.trackNumber!!.toInt())
            }
            every { this@mockk.discNumberProperty } answers {
                SimpleIntegerProperty(this, "disc number", attributes.discNumber!!.toInt())
            }
            every { this@mockk.bpmProperty } answers {
                SimpleFloatProperty(this, "bpm", attributes.bpm!!)
            }
            every { this@mockk.lastDateModifiedProperty } answers {
                SimpleObjectProperty(this, "last date modified", attributes.lastDateModified)
            }
            every { this@mockk.coverImageProperty } answers {
                SimpleObjectProperty(this, "cover image", Optional.of(Image(ByteArrayInputStream(coverImageBytes))))
            }
            every { this@mockk.artistsInvolvedProperty } answers {
                SimpleSetProperty(this, "artists involved", FXCollections.observableSet(this@mockk.artistsInvolved))
            }
            every { this@mockk.playCountProperty } answers {
                SimpleIntegerProperty(this, "play count", attributes.playCount.toInt())
            }
        }
    }