package net.transgressoft.commons.music.playlist

import com.google.common.base.Objects
import net.transgressoft.commons.music.audio.AudioItem

internal open class MutablePlaylistDirectory<I : AudioItem, N : AudioPlaylist<I>>(
    id: Int,
    theName: String,
    audioItems: List<I>? = null,
    playlists: Set<N>? = null,
) : ImmutablePlaylistDirectory<I, N>(id, theName, audioItems, playlists), MutableAudioPlaylistDirectory<I, N> {

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

    override fun addPlaylists(playlists: Set<N>) {
        super.addAll(playlists)
    }

    override fun removePlaylists(playlists: Set<N>) {
        super.removeAll(playlists)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val that = other as MutablePlaylistDirectory<*, *>
        return Objects.equal(name, that.name) && Objects.equal(id, that.id)
    }

    override fun hashCode() = Objects.hashCode(name, id)
}