package net.transgressoft.commons.music.playlist

import net.transgressoft.commons.music.audio.AudioItem
import net.transgressoft.commons.query.Attribute
import net.transgressoft.commons.query.BooleanQueryTerm
import net.transgressoft.commons.query.StringAttribute
import kotlin.reflect.*

sealed class PlaylistAttribute<N : AudioPlaylist<AudioItem>, V : Any>(private val dataType: KClass<V>) : Attribute<N, V> {

    companion object {
        val isDirectory: BooleanQueryTerm<AudioPlaylist<AudioItem>> =
            BooleanQueryTerm { audioPlaylist: AudioPlaylist<AudioItem> ->
                audioPlaylist.isDirectory
            }

        val isNotDirectory: BooleanQueryTerm<AudioPlaylist<AudioItem>> =
            BooleanQueryTerm { audioPlaylist: AudioPlaylist<AudioItem> ->
                audioPlaylist.isDirectory.not()
            }

        fun containsAnyAudioItemsMatching(queryPredicate: BooleanQueryTerm<AudioItem>): BooleanQueryTerm<AudioPlaylist<AudioItem>> {
            return BooleanQueryTerm { audioPlaylist: AudioPlaylist<AudioItem> ->
                audioPlaylist.audioItemsAnyMatch(queryPredicate)
            }
        }

        fun containsAllAudioItemsMatching(queryPredicate: BooleanQueryTerm<AudioItem>): BooleanQueryTerm<AudioPlaylist<AudioItem>> {
            return BooleanQueryTerm { audioPlaylist: AudioPlaylist<AudioItem> ->
                audioPlaylist.audioItemsAllMatch(queryPredicate)
            }
        }

        private val audioItemsType = object : KClass<List<AudioItem>> {
            override val annotations: List<Annotation>
                get() = TODO("Not yet implemented")
            override val constructors: Collection<KFunction<List<AudioItem>>>
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
            override val objectInstance: List<AudioItem>?
                get() = TODO("Not yet implemented")
            override val qualifiedName: String?
                get() = TODO("Not yet implemented")
            override val sealedSubclasses: List<KClass<out List<AudioItem>>>
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
        private val playlistsType = object : KClass<Set<AudioPlaylist<AudioItem>>> {
            override val annotations: List<Annotation>
                get() = TODO("Not yet implemented")
            override val constructors: Collection<KFunction<Set<AudioPlaylist<AudioItem>>>>
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
            override val objectInstance: Set<AudioPlaylist<AudioItem>>?
                get() = TODO("Not yet implemented")
            override val qualifiedName: String?
                get() = TODO("Not yet implemented")
            override val sealedSubclasses: List<KClass<out Set<AudioPlaylist<AudioItem>>>>
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
    }

    object NAME : PlaylistAttribute<AudioPlaylist<AudioItem>, String>(String::class), StringAttribute<AudioPlaylist<AudioItem>>

    object AUDIO_ITEMS : PlaylistAttribute<AudioPlaylist<AudioItem>, List<AudioItem>>(audioItemsType), Attribute<AudioPlaylist<AudioItem>, List<AudioItem>>
    object PLAYLISTS : PlaylistAttribute<AudioPlaylist<AudioItem>, Set<AudioPlaylist<AudioItem>>>(playlistsType), Attribute<AudioPlaylist<AudioItem>, Set<AudioPlaylist<AudioItem>>>
}