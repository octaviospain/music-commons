package net.transgressoft.commons.music.playlist

import net.transgressoft.commons.music.audio.AudioItem

internal open class ImmutablePlaylist(
    id: Int,
    isDirectory: Boolean,
    name: String,
    audioItems: List<AudioItem> = emptyList(),
    playlists: Set<AudioPlaylist<AudioItem>> = emptySet()
) : ImmutablePlaylistBase<AudioItem>(id, isDirectory, name, audioItems, playlists) {

    override fun toMutablePlaylist(): MutableAudioPlaylist<AudioItem> = MutablePlaylist(id, isDirectory, name, audioItems.toMutableList(), playlists.toMutableSet())

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ImmutablePlaylist

        if (isDirectory != other.isDirectory) return false
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

    override fun toString(): String {
        return "ImmutablePlaylist(id=$id, isDirectory=$isDirectory, name='$name', audioItems=$audioItems, playlists=$playlists)"
    }
}