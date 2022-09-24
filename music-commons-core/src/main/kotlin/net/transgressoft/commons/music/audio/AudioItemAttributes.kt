package net.transgressoft.commons.music.audio

import net.transgressoft.commons.music.audio.AlbumAttribute.ALBUM
import net.transgressoft.commons.music.audio.ArtistAttribute.ARTIST
import net.transgressoft.commons.music.audio.ArtistsInvolvedAttribute.ARTISTS_INVOLVED
import net.transgressoft.commons.music.audio.AudioItemAttribute.ALBUM.artistsInvolvedType
import net.transgressoft.commons.music.audio.AudioItemDurationAttribute.DURATION
import net.transgressoft.commons.music.audio.AudioItemFloatAttribute.BPM
import net.transgressoft.commons.music.audio.AudioItemGenreAttribute.GENRE
import net.transgressoft.commons.music.audio.AudioItemIntegerAttribute.BITRATE
import net.transgressoft.commons.music.audio.AudioItemLocalDateTimeAttribute.DATE_OF_CREATION
import net.transgressoft.commons.music.audio.AudioItemLocalDateTimeAttribute.LAST_DATE_MODIFIED
import net.transgressoft.commons.music.audio.AudioItemPathAttribute.PATH
import net.transgressoft.commons.music.audio.AudioItemShortAttribute.DISC_NUMBER
import net.transgressoft.commons.music.audio.AudioItemShortAttribute.TRACK_NUMBER
import net.transgressoft.commons.music.audio.AudioItemStringAttribute.*
import net.transgressoft.commons.query.*
import java.nio.file.Path
import java.time.Duration
import java.time.LocalDateTime
import kotlin.reflect.*

open class AudioItemAttributes(attributeSet: AudioItemAttributes?) : ImmutableAttributeSet<AudioItem>(attributeSet) {

    constructor(
        path: Path,
        title: String,
        artist: Artist,
        artistsInvolved: Set<String>,
        album: Album?,
        genre: Genre?,
        comments: String?,
        trackNumber: Short?,
        discNumber: Short?,
        bpm: Float?,
        duration: Duration,
        bitRate: Int,
        encoder: String?,
        encoding: String?,
        dateOfCreation: LocalDateTime,
    ) : this(buildAttributeSet<AudioItem> {
        set(PATH, path)
        set(TITLE, title)
        set(ARTIST, artist)
        set(ARTISTS_INVOLVED, artistsInvolved)
        set(ALBUM, album)
        set(GENRE, genre)
        set(COMMENTS, comments)
        set(TRACK_NUMBER, trackNumber)
        set(DISC_NUMBER, discNumber)
        set(BPM, bpm)
        set(DURATION, duration)
        set(BITRATE, bitRate)
        set(ENCODER, encoder)
        set(ENCODING, encoding)
        set(DATE_OF_CREATION, dateOfCreation)
        set(LAST_DATE_MODIFIED, dateOfCreation)
    } as AudioItemAttributes)

    fun <V : Any> modifiedCopyWithModifiedTime(attribute: Attribute<AudioItem, V>, value: V): AudioItemAttributes =
        MutableAudioItemAttributes(this).also {
            it[attribute] = value
            it[LAST_DATE_MODIFIED] = LocalDateTime.now()
        }
}

private class MutableAudioItemAttributes(attributeSet: AudioItemAttributes) : AudioItemAttributes(attributeSet) {
    operator fun <A : Attribute<AudioItem, V>, V : Any> set(attribute: A, value: V) {
        attributesToValues[attribute] = value
    }
}

sealed class AudioItemAttribute<I : AudioItem, V : Any>(private val dataType: KClass<V>) : Attribute<I, V> {
    object ARTIST : AudioItemAttribute<AudioItem, Artist>(Artist::class), ArtistAttribute
    object ALBUM : AudioItemAttribute<AudioItem, Album>(Album::class), AlbumAttribute
    object TITLE : AudioItemAttribute<AudioItem, String>(String::class), StringAttribute<AudioItem>
    object GENRE : AudioItemAttribute<AudioItem, Genre>(Genre::class), Attribute<AudioItem, Genre>
    object GENRE_NAME : AudioItemAttribute<AudioItem, String>(String::class), StringAttribute<AudioItem>
    object COMMENTS : AudioItemAttribute<AudioItem, String>(String::class), StringAttribute<AudioItem>
    object ENCODER : AudioItemAttribute<AudioItem, String>(String::class), StringAttribute<AudioItem>
    object ENCODING : AudioItemAttribute<AudioItem, String>(String::class), StringAttribute<AudioItem>

    internal val artistsInvolvedType = object : KClass<Set<String>> {
        override val annotations: List<Annotation>
            get() = TODO("Not yet implemented")
        override val constructors: Collection<KFunction<Set<String>>>
            get() = TODO("Not yet implemented")
        override val isAbstract: Boolean
            get() = TODO("Not yet implemented")
        override val isCompanion: Boolean
            get() = TODO("Not yet implemented")
        override val isData: Boolean
            get() = TODO("Not yet implemented")
        override val isFinal: Boolean
            get() = TODO("Not yet implemented")
        override val isFun: Boolean
            get() = TODO("Not yet implemented")
        override val isInner: Boolean
            get() = TODO("Not yet implemented")
        override val isOpen: Boolean
            get() = TODO("Not yet implemented")
        override val isSealed: Boolean
            get() = TODO("Not yet implemented")
        override val isValue: Boolean
            get() = TODO("Not yet implemented")
        override val members: Collection<KCallable<*>>
            get() = TODO("Not yet implemented")
        override val nestedClasses: Collection<KClass<*>>
            get() = TODO("Not yet implemented")
        override val objectInstance: Set<String>?
            get() = TODO("Not yet implemented")
        override val qualifiedName: String?
            get() = TODO("Not yet implemented")
        override val sealedSubclasses: List<KClass<out Set<String>>>
            get() = TODO("Not yet implemented")
        override val simpleName: String?
            get() = TODO("Not yet implemented")
        override val supertypes: List<KType>
            get() = TODO("Not yet implemented")
        override val typeParameters: List<KTypeParameter>
            get() = TODO("Not yet implemented")
        override val visibility: KVisibility?
            get() = TODO("Not yet implemented")

        override fun equals(other: Any?): Boolean {
            TODO("Not yet implemented")
        }

        override fun hashCode(): Int {
            TODO("Not yet implemented")
        }

        override fun isInstance(value: Any?): Boolean {
            TODO("Not yet implemented")
        }

    }
    object ARTISTS_INVOLVED : AudioItemAttribute<AudioItem, Set<String>>(artistsInvolvedType)
}

interface ArtistAttribute : Attribute<AudioItem, Artist> {
    fun <E : AudioItem> nameEqualsTo(name: String, ignoreCase: Boolean): BooleanQueryTerm<E> {
        return BooleanQueryTerm { audioItem ->
            audioItem[this@ArtistAttribute]?.let { it.name.equals(name, ignoreCase) } ?: false
        }
    }
}

interface AlbumAttribute : Attribute<AudioItem, Album> {
    fun <E : AudioItem> nameEqualsTo(name: String, ignoreCase: Boolean = false): BooleanQueryTerm<E> {
        return BooleanQueryTerm { audioItem ->
            audioItem[this@AlbumAttribute]?.let { it.name.equals(name, ignoreCase) } ?: false
        }
    }
}

enum class ArtistsInvolvedAttribute : SetAttribute<AudioItem, String> { ARTISTS_INVOLVED }

enum class AudioItemPathAttribute : PathAttribute<AudioItem> { PATH }

enum class AudioItemIntegerAttribute : IntegerAttribute<AudioItem> { BITRATE }

enum class AudioItemFloatAttribute : FloatAttribute<AudioItem> { BPM }

enum class AudioItemShortAttribute : ShortAttribute<AudioItem> { TRACK_NUMBER, DISC_NUMBER, }

enum class AudioItemLocalDateTimeAttribute : LocalDateTimeAttribute<AudioItem> { DATE_OF_CREATION, LAST_DATE_MODIFIED }

enum class AudioItemDurationAttribute : DurationAttribute<AudioItem> { DURATION }