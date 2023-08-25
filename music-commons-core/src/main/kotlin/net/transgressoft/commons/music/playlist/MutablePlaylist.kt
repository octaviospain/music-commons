package net.transgressoft.commons.music.playlist

import net.transgressoft.commons.music.audio.AudioItem

internal class MutablePlaylist(
    id: Int,
    isDirectory: Boolean,
    name: String,
    audioItems: List<AudioItem> = listOf(),
    playlists: Set<AudioPlaylist<AudioItem>> = setOf()
) : MutablePlaylistBase<AudioItem>(id, isDirectory, name, audioItems, playlists) {

    @Suppress("UNCHECKED_CAST")
    override fun <P : AudioPlaylist<AudioItem>> toAudioPlaylist(): P = ImmutablePlaylist(id, isDirectory, name, audioItems.toList(), playlists.toSet()) as P

    override fun toMutablePlaylist(): MutableAudioPlaylist<AudioItem> = this

    override fun toString() = "MutablePlaylist(id=$id, isDirectory=$isDirectory, name='$name', audioItems=$audioItems, playlists=$playlists)"
}

fun <P : AudioPlaylist<I>, I : AudioItem> Collection<MutableAudioPlaylist<I>>.toAudioPlaylists(): Set<P> = map<MutableAudioPlaylist<I>, P> { it.toAudioPlaylist() }.toSet()

fun <P : AudioPlaylist<I>, I : AudioItem> Collection<P>.toMutablePlaylists(): Set<MutableAudioPlaylist<I>> = map { it.toMutablePlaylist() }.toSet()