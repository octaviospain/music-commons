package net.transgressoft.commons.music.audio

import net.transgressoft.commons.music.audio.AudioItemAttribute.*
import net.transgressoft.commons.music.audio.AudioItemAttribute.ALBUM.artistsInvolvedType
import net.transgressoft.commons.query.*
import java.nio.file.Path
import java.time.Duration
import java.time.LocalDateTime
import kotlin.reflect.*

open class AudioItemAttributes(attributeSet: AudioItemAttributes?) : ImmutableAttributeSet<AudioItem>(attributeSet) {

    constructor(audioItem: AudioItem) : this(
        audioItem.path,
        audioItem.title,
        audioItem.artist,
        audioItem.album,
        audioItem.genre,
        audioItem.comments,
        audioItem.trackNumber,
        audioItem.discNumber,
        audioItem.bpm,
        audioItem.duration,
        audioItem.bitRate,
        audioItem.encoder,
        audioItem.encoding,
        audioItem.dateOfCreation,
        audioItem.lastDateModified
    )

    constructor(
        path: Path,
        title: String,
        artist: Artist = ImmutableArtist.UNKNOWN,
        album: Album = ImmutableAlbum.UNKNOWN,
        genre: Genre = Genre.UNDEFINED,
        comments: String? = null,
        trackNumber: Short? = null,
        discNumber: Short? = null,
        bpm: Float? = null,
        duration: Duration,
        bitRate: Int,
        encoder: String? = null,
        encoding: String? = null,
        dateOfCreation: LocalDateTime,
        lastDateModified: LocalDateTime,
    ) : this(buildAudioItemAttributes {
        set(PATH, path)
        set(TITLE, title)
        set(ARTIST, artist)
        set(ARTISTS_INVOLVED, AudioItemUtils.getArtistsNamesInvolved(title, artist.name, album.albumArtist.name))
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
        set(LAST_DATE_MODIFIED, lastDateModified)
    })

    fun <V : Any> copyWithModifiedTime(attribute: Attribute<AudioItem, V>, value: V): AudioItemAttributes =
        MutableAudioItemAttributes(this).also {
            it[attribute] = value
            it[LAST_DATE_MODIFIED] = LocalDateTime.now()
        }
}

internal inline fun buildAudioItemAttributes(builderAction: MutableAudioItemAttributes.() -> Unit): AudioItemAttributes {
    return MutableAudioItemAttributes().also { it.builderAction() }
}

internal class MutableAudioItemAttributes(attributeSet: AudioItemAttributes? = null) : AudioItemAttributes(attributeSet) {
    operator fun <A : Attribute<AudioItem, V>, V : Any> set(attribute: A, value: V?) {
        attributesToValues[attribute] = value
    }
}

sealed class AudioItemAttribute<I : AudioItem, V : Any>(private val dataType: KClass<V>) : Attribute<I, V> {
    object ARTIST : AudioItemAttribute<AudioItem, Artist>(Artist::class), ArtistAttribute
    object ALBUM : AudioItemAttribute<AudioItem, Album>(Album::class), AlbumAttribute
    object TITLE : AudioItemAttribute<AudioItem, String>(String::class), StringAttribute<AudioItem>
    object GENRE : AudioItemAttribute<AudioItem, Genre>(Genre::class), GenreAttribute
    object COMMENTS : AudioItemAttribute<AudioItem, String>(String::class), StringAttribute<AudioItem>
    object ENCODER : AudioItemAttribute<AudioItem, String>(String::class), StringAttribute<AudioItem>
    object ENCODING : AudioItemAttribute<AudioItem, String>(String::class), StringAttribute<AudioItem>
    object PATH : AudioItemAttribute<AudioItem, Path>(Path::class), PathAttribute<AudioItem>
    object BITRATE : AudioItemAttribute<AudioItem, Int>(Int::class), IntegerAttribute<AudioItem>
    object BPM : AudioItemAttribute<AudioItem, Float>(Float::class), FloatAttribute<AudioItem>
    object TRACK_NUMBER : AudioItemAttribute<AudioItem, Short>(Short::class), ShortAttribute<AudioItem>
    object DISC_NUMBER : AudioItemAttribute<AudioItem, Short>(Short::class), ShortAttribute<AudioItem>
    object DATE_OF_CREATION : AudioItemAttribute<AudioItem, LocalDateTime>(LocalDateTime::class), LocalDateTimeAttribute<AudioItem>
    object LAST_DATE_MODIFIED : AudioItemAttribute<AudioItem, LocalDateTime>(LocalDateTime::class), LocalDateTimeAttribute<AudioItem>
    object DURATION : AudioItemAttribute<AudioItem, Duration>(Duration::class), DurationAttribute<AudioItem>

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

    object ARTISTS_INVOLVED : AudioItemAttribute<AudioItem, Set<String>>(artistsInvolvedType), SetAttribute<AudioItem, String>
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

interface GenreAttribute : Attribute<AudioItem, Genre> {
    fun <E : AudioItem> contains(string: String): BooleanQueryTerm<E> {
        return BooleanQueryTerm { audioItem ->
            audioItem[this@GenreAttribute]?.let { it.name.contains(string) } ?: false
        }
    }
}