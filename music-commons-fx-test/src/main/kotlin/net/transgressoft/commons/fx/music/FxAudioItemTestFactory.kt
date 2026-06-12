package net.transgressoft.commons.fx.music

import net.transgressoft.commons.fx.music.audio.ObservableAudioItem
import net.transgressoft.commons.music.audio.Album
import net.transgressoft.commons.music.audio.Artist
import net.transgressoft.commons.music.audio.AudioItemMetadata
import net.transgressoft.commons.music.audio.AudioItemTestAttributes
import net.transgressoft.commons.music.audio.Genre
import net.transgressoft.commons.music.audio.audioAttributes
import net.transgressoft.commons.music.audio.audioItemTrackDiscNumberComparator
import net.transgressoft.lirp.entity.ReactiveEntityBase
import io.kotest.property.Arb
import io.kotest.property.arbitrary.next
import javafx.beans.property.FloatProperty
import javafx.beans.property.IntegerProperty
import javafx.beans.property.ObjectProperty
import javafx.beans.property.ReadOnlyIntegerProperty
import javafx.beans.property.ReadOnlyObjectProperty
import javafx.beans.property.ReadOnlyProperty
import javafx.beans.property.ReadOnlySetProperty
import javafx.beans.property.SimpleFloatProperty
import javafx.beans.property.SimpleIntegerProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.property.SimpleSetProperty
import javafx.beans.property.SimpleStringProperty
import javafx.beans.property.StringProperty
import javafx.collections.FXCollections
import javafx.scene.image.Image
import java.nio.file.Path
import java.time.Duration
import java.time.LocalDateTime
import java.util.Optional
import java.util.function.Consumer

/**
 * Java-friendly factory methods for observable audio item test fixtures.
 *
 * This object delegates to the Kotlin fxAudioItem generator so Java tests can reuse the
 * same mocked [ObservableAudioItem] construction path as Kotlin tests.
 */
object FxAudioItemTestFactory {

    /**
     * Creates an observable audio item with caller-supplied test attributes.
     *
     * @param attributes action that mutates the generated attributes before the item is built
     * @return observable audio item backed by music-commons-fx-test mocks
     */
    @JvmStatic
    fun createFxAudioItem(attributes: Consumer<AudioItemTestAttributes>): ObservableAudioItem =
        Arb.fxAudioItem(attributes::accept).next()

    /**
     * Creates an observable audio item with an explicit involved-artist set.
     *
     * Use this overload for tests whose assertion depends on exact derived-artist membership and
     * not on the title parser heuristics. It returns a lightweight concrete item with real JavaFX
     * properties so UI tests do not depend on MockK-generated property subclasses at render time.
     *
     * @param attributes action that mutates the generated attributes before the item is built
     * @param artistsInvolved exact artist set returned by the item and its JavaFX property
     * @return observable audio item backed by music-commons-fx-test mocks
     */
    @JvmStatic
    fun createFxAudioItem(attributes: Consumer<AudioItemTestAttributes>, artistsInvolved: Set<Artist>): ObservableAudioItem {
        val generated = Arb.audioAttributes().next()
        attributes.accept(generated)
        return TestObservableAudioItem(generated, artistsInvolved)
    }

    private class TestObservableAudioItem(
        attributes: AudioItemTestAttributes,
        override val artistsInvolved: Set<Artist>
    ) : ObservableAudioItem,
        Comparable<ObservableAudioItem>,
        ReactiveEntityBase<Int, ObservableAudioItem>() {

        private val metadata = attributes.metadata

        override val id: Int = attributes.id
        override val uniqueId: String = "${attributes.path.fileName}-${metadata.title}-${attributes.id}"
        override val path: Path = attributes.path
        override val duration: Duration = metadata.duration
        override val bitRate: Int = metadata.bitRate
        override val encoder: String? = metadata.encoder
        override val encoding: String? = metadata.encoding
        override val dateOfCreation: LocalDateTime = attributes.dateOfCreation
        override val playCount: Short = attributes.playCount
        override val fileName: String = path.fileName.toString()
        override val extension: String = path.fileName.toString().substringAfterLast('.', "")
        override val length: Long = 0

        override val titleProperty: StringProperty = SimpleStringProperty(this, "title", metadata.title)
        override var title: String
            get() = titleProperty.get()
            set(value) = titleProperty.set(value)

        override val artistProperty: ObjectProperty<Artist> = SimpleObjectProperty(this, "artist", metadata.artist)
        override var artist: Artist
            get() = artistProperty.get()
            set(value) = artistProperty.set(value)

        override val albumProperty: ObjectProperty<Album> = SimpleObjectProperty(this, "album", metadata.album)
        override var album: Album
            get() = albumProperty.get()
            set(value) = albumProperty.set(value)

        override val genresProperty: ObjectProperty<Set<Genre>> = SimpleObjectProperty(this, "genres", metadata.genres)
        override var genres: Set<Genre>
            get() = genresProperty.get()
            set(value) = genresProperty.set(value)

        override val commentsProperty: StringProperty = SimpleStringProperty(this, "comments", metadata.comments ?: "")
        override var comments: String?
            get() = commentsProperty.get()
            set(value) = commentsProperty.set(value)

        override val trackNumberProperty: IntegerProperty = SimpleIntegerProperty(this, "track number", metadata.trackNumber?.toInt() ?: 0)
        override var trackNumber: Short?
            get() = trackNumberProperty.get().takeIf { it > 0 }?.toShort()
            set(value) = trackNumberProperty.set(value?.toInt() ?: 0)

        override val discNumberProperty: IntegerProperty = SimpleIntegerProperty(this, "disc number", metadata.discNumber?.toInt() ?: 0)
        override var discNumber: Short?
            get() = discNumberProperty.get().takeIf { it > 0 }?.toShort()
            set(value) = discNumberProperty.set(value?.toInt() ?: 0)

        override val bpmProperty: FloatProperty = SimpleFloatProperty(this, "bpm", metadata.bpm ?: 0f)
        override var bpm: Float?
            get() = bpmProperty.get().takeIf { it > 0f }
            set(value) = bpmProperty.set(value ?: 0f)

        override val coverImageProperty: ReadOnlyObjectProperty<Optional<Image>> =
            SimpleObjectProperty(this, "cover image", Optional.empty())

        override val artistsInvolvedProperty: ReadOnlySetProperty<Artist> =
            SimpleSetProperty(this, "artists involved", FXCollections.observableSet(artistsInvolved))

        override val lastDateModifiedProperty: ReadOnlyObjectProperty<LocalDateTime> =
            SimpleObjectProperty(this, "last date modified", attributes.lastDateModified)

        override val dateOfCreationProperty: ReadOnlyProperty<LocalDateTime> =
            SimpleObjectProperty(this, "date of creation", dateOfCreation)

        override val playCountProperty: ReadOnlyIntegerProperty =
            SimpleIntegerProperty(this, "play count", playCount.toInt())

        override var coverImageBytes: ByteArray? = metadata.coverBytes

        override fun setPlayCount(count: Short) {
            // Immutable test fixture: play count changes are not needed by current consumers.
        }

        override fun compareTo(other: ObservableAudioItem): Int =
            audioItemTrackDiscNumberComparator<ObservableAudioItem>().compare(this, other)

        override fun clone(): ObservableAudioItem =
            TestObservableAudioItem(
                AudioItemTestAttributes(
                    path = path,
                    id = id,
                    metadata =
                        AudioItemMetadata(
                            title = title,
                            artist = artist,
                            album = album,
                            genres = genres,
                            comments = comments,
                            trackNumber = trackNumber,
                            discNumber = discNumber,
                            bpm = bpm,
                            encoder = encoder,
                            encoding = encoding,
                            bitRate = bitRate,
                            duration = duration,
                            coverBytes = coverImageBytes
                        ),
                    dateOfCreation = dateOfCreation,
                    lastDateModified = lastDateModified,
                    playCount = playCount
                ),
                artistsInvolved
            )
    }
}