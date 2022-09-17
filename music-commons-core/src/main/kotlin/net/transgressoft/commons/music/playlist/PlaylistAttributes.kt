package net.transgressoft.commons.music.playlist

import net.transgressoft.commons.music.audio.AudioItem
import net.transgressoft.commons.music.playlist.PlaylistAudioItemsAttribute.AUDIO_ITEMS
import net.transgressoft.commons.music.playlist.PlaylistDescendantsAttribute.PLAYLISTS
import net.transgressoft.commons.music.playlist.PlaylistStringAttribute.NAME
import net.transgressoft.commons.query.*

open class PlaylistAttributes<I : AudioItem>(
    name: String,
    audioItems: List<I> = emptyList(),
    playlists: List<AudioPlaylist<I>> = emptyList(),
) :
    MutableAttributeSet<AudioPlaylist<I>>(buildAttributeSet {
        set(NAME as Attribute<AudioPlaylist<I>, Any>, name)
        set(AUDIO_ITEMS as Attribute<AudioPlaylist<I>, Any>, audioItems.toMutableList())
        set(PLAYLISTS as Attribute<AudioPlaylist<I>, Any>, playlists.toMutableSet())
    })

enum class PlaylistStringAttribute : StringAttribute<AudioPlaylist<AudioItem>> { NAME }

enum class PlaylistAudioItemsAttribute : ListAttribute<AudioPlaylist<out AudioItem>, AudioItem> { AUDIO_ITEMS }

enum class PlaylistDescendantsAttribute : SetAttribute<AudioPlaylist<AudioItem>, AudioPlaylist<AudioItem>> { PLAYLISTS }

enum class PlaylistAttribute : Attribute<AudioPlaylist<AudioItem>, AudioPlaylist<AudioItem>> {
    IT;

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
}