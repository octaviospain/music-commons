package net.transgressoft.commons.music.playlist

import net.transgressoft.commons.music.audio.AudioItem

internal class MutablePlaylist(
    id: Int,
    isDirectory: Boolean,
    name: String,
    audioItems: List<AudioItem> = listOf(),
    playlists: Set<MutableAudioPlaylist<AudioItem>> = setOf()
) : MutablePlaylistBase<AudioItem>(id, isDirectory, name, audioItems, playlists) {

    override fun toAudioPlaylist(): AudioPlaylist<AudioItem> =
        if (isDirectory) {
            ImmutablePlaylistDirectory(id, name, audioItems.toList(), playlists.toSet())
        } else {
            ImmutablePlaylist(id, name, audioItems.toList(), playlists.toSet())
        }

    override fun toString() = "MutablePlaylist(id=$id, isDirectory=$isDirectory, name='$name', audioItems=$audioItems, playlists=$playlists)"
}

fun Collection<MutableAudioPlaylist<AudioItem>>.toAudioPlaylists(): Set<AudioPlaylist<AudioItem>> = map { it.toAudioPlaylist() }.toSet()

internal fun AudioPlaylist<AudioItem>.toMutablePlaylist(): MutablePlaylist =
    MutablePlaylist(id, isDirectory, name, audioItems.toMutableList(), playlists.toMutablePlaylists())

internal fun Collection<AudioPlaylist<AudioItem>>.toMutablePlaylists(): Set<MutableAudioPlaylist<AudioItem>> = map { it.toMutablePlaylist() }.toSet()