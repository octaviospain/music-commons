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

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MutablePlaylist

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

    override fun toString() = "MutablePlaylist(id=$id, isDirectory=$isDirectory, name='$name', audioItems=$audioItems, playlists=$playlists)"
}

fun Collection<MutableAudioPlaylist<AudioItem>>.toAudioPlaylists(): Set<AudioPlaylist<AudioItem>> = map { it.toAudioPlaylist() }.toSet()

internal fun AudioPlaylist<AudioItem>.toMutablePlaylist(): MutablePlaylist =
    MutablePlaylist(id, isDirectory, name, audioItems.toMutableList(), playlists.toMutablePlaylists())

internal fun Collection<AudioPlaylist<AudioItem>>.toMutablePlaylists(): Set<MutableAudioPlaylist<AudioItem>> = map { it.toMutablePlaylist() }.toSet()