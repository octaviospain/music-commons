package net.transgressoft.commons.music.playlist

import net.transgressoft.commons.music.audio.AudioItem
import net.transgressoft.commons.query.BooleanQueryTerm
import net.transgressoft.commons.query.EntityAttribute

enum class PlaylistNodeAttribute : EntityAttribute<AudioPlaylist<AudioItem>> {
    SELF;

    val isDirectory: BooleanQueryTerm<AudioPlaylist<AudioItem>>
        get() = BooleanQueryTerm { queryEntity: AudioPlaylist<AudioItem> -> queryEntity.getAttribute(this).isDirectory }
    val isNotDirectory: BooleanQueryTerm<AudioPlaylist<AudioItem>>
        get() = BooleanQueryTerm { queryEntity: AudioPlaylist<AudioItem> -> !queryEntity.getAttribute(this).isDirectory }

    fun audioItemsAnyMatch(queryPredicate: BooleanQueryTerm<AudioItem>): BooleanQueryTerm<AudioPlaylist<AudioItem>> {
        return BooleanQueryTerm { queryEntity: AudioPlaylist<AudioItem> ->
            queryEntity.getAttribute(SELF).audioItemsAnyMatch(queryPredicate)
        }
    }

    fun audioItemsAllMatch(queryPredicate: BooleanQueryTerm<AudioItem>): BooleanQueryTerm<AudioPlaylist<AudioItem>> {
        return BooleanQueryTerm { queryEntity: AudioPlaylist<AudioItem> ->
            queryEntity.getAttribute(SELF).audioItemsAllMatch(queryPredicate)
        }
    }
}