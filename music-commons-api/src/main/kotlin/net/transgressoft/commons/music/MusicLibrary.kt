package net.transgressoft.commons.music

import net.transgressoft.commons.event.QueryEntitySubscriber
import net.transgressoft.commons.music.audio.AudioItem
import net.transgressoft.commons.music.audio.AudioItemRepository
import net.transgressoft.commons.music.playlist.AudioPlaylist
import net.transgressoft.commons.music.playlist.AudioPlaylistDirectory
import net.transgressoft.commons.music.playlist.AudioPlaylistRepository
import net.transgressoft.commons.music.waveform.AudioWaveform
import net.transgressoft.commons.music.waveform.AudioWaveformRepository
import java.util.concurrent.CompletableFuture

interface MusicLibrary<I : AudioItem, P : AudioPlaylist<I>, D : AudioPlaylistDirectory<I>, W : AudioWaveform> {

    val audioItemRepository: AudioItemRepository<I>
    val audioPlaylistRepository: AudioPlaylistRepository<I, P, D>
    val audioWaveformRepository: AudioWaveformRepository<W>
    val audioItemSubscriber: QueryEntitySubscriber<I>

    fun artists(): Set<String>

    fun deleteAudioItems(audioItems: Set<I>)

    /**
     * Precondition, <tt>playlist</tt> exist in the <tt>AudioPlaylistRepository</tt>.
     * Otherwise, no action is performed.
     *
     * @param audioItems
     * @param playlist
     */
    fun addAudioItemsToPlaylist(audioItems: Collection<I>, playlist: P)

    /**
     * Precondition, <tt>playlist</tt> exist in the <tt>AudioPlaylistRepository</tt>.
     * Otherwise, no action is performed.
     *
     * @param audioItems
     * @param playlist
     */
    fun removeAudioItemsFromPlaylist(audioItems: Collection<I>, playlist: P)
    fun movePlaylist(playlist: P, playlistDirectory: D)
    fun getOrCreateWaveformAsync(audioItem: I, width: Short, height: Short): CompletableFuture<W>
}