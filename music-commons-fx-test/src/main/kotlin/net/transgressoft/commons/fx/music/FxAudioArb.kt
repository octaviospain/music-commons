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
        val metadata = attributes.metadata
        mockk<FXAudioItem>(relaxed = true) {
            // immutable properties
            every { id } returns attributes.id
            every { path } returns attributes.path
            every { duration } returns metadata.duration
            every { bitRate } returns metadata.bitRate
            every { encoder } returns metadata.encoder
            every { encoding } returns metadata.encoding
            every { dateOfCreation } returns attributes.dateOfCreation
            every { lastDateModified } returns attributes.lastDateModified
            every { playCount } returns attributes.playCount
            every { fileName } returns attributes.path.fileName.toString()
            every { extension } returns attributes.path.extension
            every { length } returns path.toFile().length()

            // mutable properties
            every { title } returns metadata.title
            every { artist } returns metadata.artist
            every { album } returns metadata.album
            every { genres } returns metadata.genres
            every { comments } returns metadata.comments
            every { trackNumber } returns metadata.trackNumber
            every { discNumber } returns metadata.discNumber
            every { bpm } returns metadata.bpm
            every { coverImageBytes } returns metadata.coverBytes
            every { playCount } returns attributes.playCount

            every { this@mockk.dateOfCreationProperty } answers {
                SimpleObjectProperty(this, "date of creation", attributes.dateOfCreation)
            }
            every { this@mockk.artistProperty } answers {
                SimpleObjectProperty(this, "artist", metadata.artist)
            }
            every { this@mockk.albumProperty } answers {
                SimpleObjectProperty(this, "album", metadata.album)
            }
            every { this@mockk.genresProperty } answers {
                SimpleObjectProperty(this, "genres", metadata.genres)
            }
            every { this@mockk.commentsProperty } answers {
                SimpleStringProperty(this, "comments", metadata.comments)
            }
            every { this@mockk.trackNumberProperty } answers {
                SimpleIntegerProperty(this, "track number", metadata.trackNumber?.toInt() ?: -1)
            }
            every { this@mockk.discNumberProperty } answers {
                SimpleIntegerProperty(this, "disc number", metadata.discNumber?.toInt() ?: -1)
            }
            every { this@mockk.bpmProperty } answers {
                SimpleFloatProperty(this, "bpm", metadata.bpm ?: -1f)
            }
            every { this@mockk.lastDateModifiedProperty } answers {
                SimpleObjectProperty(this, "last date modified", attributes.lastDateModified)
            }
            every { this@mockk.coverImageProperty } answers {
                val image = coverImageBytes?.let { Optional.of(Image(ByteArrayInputStream(it))) } ?: Optional.empty()
                SimpleObjectProperty(this, "cover image", image)
            }
            every { this@mockk.artistsInvolvedProperty } answers {
                SimpleSetProperty(this, "artists involved", FXCollections.observableSet(this@mockk.artistsInvolved))
            }
            every { this@mockk.playCountProperty } answers {
                SimpleIntegerProperty(this, "play count", attributes.playCount.toInt())
            }
        }
    }