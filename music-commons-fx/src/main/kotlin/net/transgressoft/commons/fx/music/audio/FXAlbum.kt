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

import net.transgressoft.commons.music.audio.AlbumDetails
import net.transgressoft.commons.music.audio.canonicalKey
import net.transgressoft.commons.music.audio.firstCoverImageBytes
import net.transgressoft.commons.music.audio.id
import net.transgressoft.lirp.entity.ReactiveEntityBase
import javafx.application.Platform
import javafx.beans.property.ReadOnlyBooleanProperty
import javafx.beans.property.ReadOnlyBooleanWrapper
import javafx.beans.property.ReadOnlyIntegerProperty
import javafx.beans.property.ReadOnlyListProperty
import javafx.beans.property.ReadOnlyObjectProperty
import javafx.beans.property.SimpleIntegerProperty
import javafx.beans.property.SimpleListProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.collections.FXCollections
import javafx.scene.image.Image
import mu.KotlinLogging
import java.io.ByteArrayInputStream
import java.lang.ref.SoftReference
import java.util.Optional

/**
 * JavaFX implementation of [ObservableAlbum] that is built once from a list snapshot.
 *
 * Audio items are held in a flat list delivered by lirp's projection, which guarantees
 * disc-then-track ordering and distinctness before this class is constructed. No internal
 * sorting or de-duplication is performed. JavaFX observable properties are populated during
 * construction and kept stable for the lifetime of the instance.
 *
 * The [album] field holds the derived representative [AlbumDetails] (most-frequent non-empty
 * value per field across the bucket's tracks), not the canonical bucket key. The [uniqueId]
 * is derived from the canonical key (`album.canonicalKey().id()`) so it remains stable when
 * the representative's year or casing changes across bucket recomputes.
 *
 * This class must be constructed only on the JavaFX Application Thread because it initializes
 * JavaFX properties and calls [SimpleListProperty.setAll] inside its init block. The registry's
 * `fxFactory` parameter guarantees this thread contract.
 */
internal class FXAlbum(
    override val album: AlbumDetails,
    audioItems: List<ObservableAudioItem>
) : ObservableAlbum,
    Comparable<ObservableAlbum>,
    ReactiveEntityBase<AlbumDetails, ObservableAlbum>() {

    private val logger = KotlinLogging.logger {}

    private val trackList: List<ObservableAudioItem> = audioItems

    override val id: AlbumDetails = album

    override val uniqueId: String = album.canonicalKey().id()

    override val size: Int get() = trackList.size

    override val isEmpty: Boolean get() = trackList.isEmpty()

    override val tracks: List<ObservableAudioItem> get() = trackList

    override val tracksProperty: ReadOnlyListProperty<ObservableAudioItem>
        field = SimpleListProperty(this, "tracks", FXCollections.observableArrayList())

    override val sizeProperty: ReadOnlyIntegerProperty
        field = SimpleIntegerProperty(this, "size", 0)

    private val _emptyProperty =
        ReadOnlyBooleanWrapper(this, "empty", true).apply {
            bind(sizeProperty.isEqualTo(0))
        }
    override val emptyProperty: ReadOnlyBooleanProperty = _emptyProperty.readOnlyProperty

    override val albumProperty: ReadOnlyObjectProperty<AlbumDetails>
        field = SimpleObjectProperty(this, "album", album)

    @Transient
    @Volatile
    private var coverBytesRef: SoftReference<ByteArray>? = null

    @Transient
    @Volatile
    private var noCover: Boolean = false

    override val coverImageBytes: ByteArray?
        get() {
            coverBytesRef?.get()?.let { return it }
            if (noCover) return null
            synchronized(this) {
                coverBytesRef?.get()?.let { return it }
                if (noCover) return null
                val bytes = firstCoverImageBytes(trackList)
                return if (bytes != null) {
                    coverBytesRef = SoftReference(bytes)
                    publishCoverImage(bytes)
                    bytes
                } else {
                    noCover = true
                    null
                }
            }
        }

    override val coverProperty: ReadOnlyObjectProperty<Optional<Image>>
        field = SimpleObjectProperty(this, "cover", Optional.empty())

    init {
        logger.debug { "FXAlbum created for ${album.name}" }
        tracksProperty.setAll(trackList)
        sizeProperty.set(size)
    }

    override fun clone(): FXAlbum = FXAlbum(album, trackList.toList())

    override fun compareTo(other: ObservableAlbum): Int = this.album.compareTo(other.album)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FXAlbum) return false
        if (album != other.album) return false
        return trackList == other.trackList
    }

    override fun hashCode(): Int = 31 * album.hashCode() + trackList.hashCode()

    override fun toString() = "FXAlbum(album=$album, size=$size)"

    // Builds the JavaFX Image from the resolved cover bytes and publishes it into coverProperty
    // on the FX thread. Called lazily on first resolution of coverImageBytes; the resolved Image
    // is held by coverProperty, while the bytes remain softly cached via coverBytesRef.
    private fun publishCoverImage(bytes: ByteArray) {
        val image = Image(ByteArrayInputStream(bytes))
        runOnFxThread { coverProperty.set(Optional.of(image)) }
    }

    // Dispatches [action] on the FX Application Thread. Falls back to inline execution
    // when the toolkit is not yet initialized (headless test contexts).
    private fun runOnFxThread(action: () -> Unit) {
        try {
            Platform.runLater(action)
        } catch (_: IllegalStateException) {
            action()
        }
    }
}