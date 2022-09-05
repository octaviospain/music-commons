package net.transgressoft.commons.music.playlist

import com.google.common.base.MoreObjects
import com.google.common.base.Objects
import net.transgressoft.commons.music.audio.AudioItem

internal class MutablePlaylistDirectory<I : AudioItem>(
    id: Int,
    theName: String,
    audioItems: List<I> = emptyList(),
    playlists: Set<AudioPlaylist<I>> = emptySet(),
) : ImmutablePlaylistDirectory<I>(id, theName, audioItems, playlists), MutableAudioPlaylistDirectory<I> {

    override var name: String = theName
        set(value) {
            super.setNameInternal(value)
        }

    override fun addAudioItems(audioItems: Collection<I>) {
        super.addAll(audioItems)
    }

    override fun removeAudioItems(audioItems: Collection<I>) {
        super.removeAll(audioItems)
    }

    override fun clearAudioItems() {
        super.clear()
    }

    override fun <N : AudioPlaylist<I>> addPlaylists(playlists: Set<N>) {
        super.addAll(playlists)
    }

    override fun <N : AudioPlaylist<I>> removePlaylists(playlists: Set<N>) {
        super.removeAll(playlists)
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        if (o == null || javaClass != o.javaClass) return false
        val that = o as MutablePlaylistDirectory<*>
        return Objects.equal(name, that.name) && Objects.equal(id, that.id)
    }

    override fun hashCode() = Objects.hashCode(name, id)

    override fun toString(): String {
        return MoreObjects.toStringHelper(this)
            .add("id", id)
            .add("name", name)
            .add("descendantPlaylists", descendantPlaylists<AudioPlaylist<I>>().size)
            .add("audioItems", audioItems().size)
            .toString()
    }
}