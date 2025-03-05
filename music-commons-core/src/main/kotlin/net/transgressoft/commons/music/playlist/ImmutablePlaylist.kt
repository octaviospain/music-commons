package net.transgressoft.commons.music.playlist

import net.transgressoft.commons.music.audio.ReactiveAudioItem
import net.transgressoft.commons.music.audio.UNASSIGNED_ID

internal class ImmutablePlaylist<I : ReactiveAudioItem<I>, P : AudioPlaylist<I>>(
    override var id: Int = UNASSIGNED_ID,
    override val isDirectory: Boolean,
    override val name: String,
    override val audioItems: List<I> = emptyList(),
    override val playlists: Set<P> = emptySet()
) : AudioPlaylist<I> {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ImmutablePlaylist<*, *>

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

    override fun clone(): ImmutablePlaylist<I, P> = ImmutablePlaylist(id, isDirectory, name, audioItems.toList(), playlists.toSet())

    override fun toString() = "ImmutablePlaylist(id=$id, isDirectory=$isDirectory, name='$name', audioItems=$audioItems, playlists=$playlists)"
}