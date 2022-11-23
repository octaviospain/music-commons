package net.transgressoft.commons.music.playlist

import net.transgressoft.commons.music.audio.AudioItem

internal class MutablePlaylist(
    id: Int,
    isDirectory: Boolean,
    name: String,
    audioItems: List<AudioItem> = listOf(),
    playlists: Set<AudioPlaylist<AudioItem>> = setOf()
) : MutablePlaylistBase<AudioItem>(id, isDirectory, name, audioItems, playlists) {

    override fun toAudioPlaylist(): AudioPlaylist<AudioItem> = ImmutablePlaylist(id, isDirectory, name, audioItems.toList(), playlists.toSet())

    override fun toMutablePlaylist(): MutableAudioPlaylist<AudioItem> = this

    override fun toString(): String {
        return "MutablePlaylist(id=$id, isDirectory=$isDirectory, name='$name', audioItems=$audioItems, playlists=$playlists)"
    }
}