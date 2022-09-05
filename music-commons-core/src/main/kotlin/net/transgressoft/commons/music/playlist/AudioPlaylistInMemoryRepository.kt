package net.transgressoft.commons.music.playlist

import net.transgressoft.commons.music.audio.AudioItem
import java.util.stream.Collectors

open class AudioPlaylistInMemoryRepository(
    playlistsById: MutableMap<Int, MutableAudioPlaylist<AudioItem>> = HashMap(),
    directoriesById: MutableMap<Int, MutableAudioPlaylistDirectory<AudioItem>> = HashMap(),
) : AudioPlaylistInMemoryRepositoryBase<
        AudioItem,
        AudioPlaylist<AudioItem>,
        AudioPlaylistDirectory<AudioItem>,
        MutableAudioPlaylist<AudioItem>,
        MutableAudioPlaylistDirectory<AudioItem>>
    (playlistsById, directoriesById) {

    override fun toMutablePlaylist(playlistDirectory: AudioPlaylist<AudioItem>): MutableAudioPlaylist<AudioItem> =
        MutablePlaylist(getNewId(), playlistDirectory.name, playlistDirectory.audioItems())

    override fun toMutablePlaylists(audioPlaylists: Set<AudioPlaylist<AudioItem>>): Set<MutableAudioPlaylist<AudioItem>> =
        audioPlaylists.stream()
            .map {
                if (it.isDirectory) {
                    val dir = it as AudioPlaylistDirectory<AudioItem>
                    return@map MutablePlaylistDirectory(
                        dir.id,
                        dir.name,
                        dir.audioItems(),
                        dir.descendantPlaylists<AudioPlaylist<AudioItem>>()
                    )
                } else {
                    return@map MutablePlaylist(it.id, it.name, it.audioItems())
                }
            }.collect(Collectors.toSet())

    override fun toMutableDirectory(playlistDirectory: AudioPlaylistDirectory<AudioItem>): MutableAudioPlaylistDirectory<AudioItem> =
        MutablePlaylistDirectory(
            getNewId(),
            playlistDirectory.name,
            playlistDirectory.audioItems(),
            toMutablePlaylists(playlistDirectory.descendantPlaylists()))

    override fun toImmutablePlaylist(audioPlaylist: MutableAudioPlaylist<AudioItem>): AudioPlaylist<AudioItem> =
        ImmutablePlaylist(audioPlaylist.id, audioPlaylist.name, audioPlaylist.audioItems())

    override fun toImmutablePlaylist(audioPlaylist: AudioPlaylist<AudioItem>): AudioPlaylist<AudioItem> {
        return ImmutablePlaylist(audioPlaylist.id, audioPlaylist.name, audioPlaylist.audioItems())
    }

    override fun toImmutablePlaylistDirectory(playlistDirectory: MutableAudioPlaylistDirectory<AudioItem>): AudioPlaylist<AudioItem> =
//        return if (playlistDirectory == null) null else ImmutablePlaylistDirectory(
//            playlistDirectory.id, playlistDirectory.name, playlistDirectory.audioItems(),
//            toImmutablePlaylistDirectories(playlistDirectory.descendantPlaylists<AudioPlaylist<AudioItem>>())
//        )
        ImmutablePlaylistDirectory(
            playlistDirectory.id,
            playlistDirectory.name,
            playlistDirectory.audioItems(),
            toImmutablePlaylistDirectories(playlistDirectory.descendantPlaylists()))

    override fun toImmutablePlaylistDirectories(audioPlaylists: Set<AudioPlaylist<AudioItem>>): Set<AudioPlaylist<AudioItem>> =
        audioPlaylists.stream()
            .map {
                if (it.isDirectory)
                    toImmutablePlaylistDirectory(
                        it as MutableAudioPlaylistDirectory<AudioItem>
                    )
                else
                    toImmutablePlaylist(it)
            }.collect(Collectors.toSet())
}