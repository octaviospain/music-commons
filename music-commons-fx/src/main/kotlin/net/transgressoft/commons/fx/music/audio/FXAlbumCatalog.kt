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

import net.transgressoft.commons.music.audio.Album
import net.transgressoft.commons.music.audio.UNASSIGNED_ID
import net.transgressoft.commons.music.audio.audioItemIdentityComparator
import net.transgressoft.commons.music.audio.audioItemTrackDiscNumberComparator
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
import java.util.TreeSet

/**
 * JavaFX implementation of [ObservableAlbumCatalog] that is built once from a list snapshot.
 *
 * Audio items are held in a flat sorted, de-duplicated [TreeSet] built at construction time.
 * The internal set is never mutated after construction, so reads need no synchronization.
 * JavaFX observable properties are populated during construction and kept stable for the
 * lifetime of the instance.
 *
 * This class must be constructed only on the JavaFX Application Thread because it initializes
 * JavaFX properties and calls [SimpleListProperty.setAll] inside its init block. The registry's
 * `fxFactory` parameter guarantees this thread contract.
 */
internal class FXAlbumCatalog(
    override val album: Album,
    audioItems: List<ObservableAudioItem>
) : ObservableAlbumCatalog,
    Comparable<ObservableAlbumCatalog>,
    ReactiveEntityBase<Album, ObservableAlbumCatalog>() {

    private val logger = KotlinLogging.logger {}

    // Flat sorted, de-duplicated set — built once in constructor, never mutated after construction
    private val audioItemsSet: TreeSet<ObservableAudioItem> = buildFlatSet(audioItems)

    override val id: Album = album

    override val uniqueId: String = album.name

    override val size: Int get() = audioItemsSet.size

    override val isEmpty: Boolean get() = audioItemsSet.isEmpty()

    override val audioItems: Set<ObservableAudioItem> get() = audioItemsSet

    override val audioItemsProperty: ReadOnlyListProperty<ObservableAudioItem>
        field = SimpleListProperty(this, "audioItems", FXCollections.observableArrayList())

    override val sizeProperty: ReadOnlyIntegerProperty
        field = SimpleIntegerProperty(this, "size", 0)

    private val _emptyProperty =
        ReadOnlyBooleanWrapper(this, "empty", true).apply {
            bind(sizeProperty.isEqualTo(0))
        }
    override val emptyProperty: ReadOnlyBooleanProperty = _emptyProperty.readOnlyProperty

    override val albumProperty: ReadOnlyObjectProperty<Album>
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
                val bytes = firstCoverImageBytes(audioItemsSet)
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
        logger.debug { "FXAlbumCatalog created for ${album.name}" }
        audioItemsProperty.setAll(audioItemsSet.toList())
        sizeProperty.set(size)
    }

    override fun clone(): FXAlbumCatalog = FXAlbumCatalog(album, audioItemsSet.toList())

    override fun compareTo(other: ObservableAlbumCatalog): Int = this.album.compareTo(other.album)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FXAlbumCatalog) return false
        if (album != other.album) return false
        return audioItemsSet == other.audioItemsSet
    }

    override fun hashCode(): Int = 31 * album.hashCode() + audioItemsSet.hashCode()

    override fun toString() = "FXAlbumCatalog(album=$album, size=$size)"

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

    companion object {

        // Items sorted by disc/track number with identity tie-break so that two distinct
        // items with the same disc/track are both retained in the flat TreeSet bucket.
        private val audioItemComparator: Comparator<ObservableAudioItem> =
            audioItemTrackDiscNumberComparator<ObservableAudioItem>()
                .thenComparing(audioItemIdentityComparator())

        private fun buildFlatSet(audioItems: List<ObservableAudioItem>): TreeSet<ObservableAudioItem> {
            val result = TreeSet(audioItemComparator)
            for (item in audioItems) {
                if (result.none { isSameAudioItem(it, item) }) {
                    result.add(item)
                }
            }
            return result
        }

        private fun isSameAudioItem(left: ObservableAudioItem, right: ObservableAudioItem): Boolean =
            if (left.id != UNASSIGNED_ID && right.id != UNASSIGNED_ID) {
                left.id == right.id
            } else {
                left === right || left.uniqueId == right.uniqueId
            }
    }
}