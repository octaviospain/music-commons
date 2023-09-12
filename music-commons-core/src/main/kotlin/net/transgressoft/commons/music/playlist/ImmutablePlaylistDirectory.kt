package net.transgressoft.commons.music.playlist

import net.transgressoft.commons.music.audio.AudioItem

data class ImmutablePlaylistDirectory(
    override val name: String,
    override val audioItems: List<AudioItem> = emptyList(),
    override val playlists: Set<AudioPlaylist<AudioItem>> = emptySet()
) : ImmutablePlaylistBase<AudioItem>(name, audioItems, playlists) {

    internal constructor(id: Int, name: String, audioItems: List<AudioItem>, playlists: Set<AudioPlaylist<AudioItem>>)
            : this(name, audioItems, playlists) {
        this.id = id
    }

    override val isDirectory = true

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ImmutablePlaylistDirectory

        if (name != other.name) return false
        if (audioItems != other.audioItems) return false
        if (playlists != other.playlists) return false

        return true
    }

    override fun hashCode(): Int {
        var result = isDirectory.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + audioItems.hashCode()
        result = 31 * result + playlists.hashCode()
        return result
    }

    override fun toString() = "ImmutablePlaylistDirectory(id=$id, name='$name', audioItems=$audioItems, playlists=$playlists)"
}