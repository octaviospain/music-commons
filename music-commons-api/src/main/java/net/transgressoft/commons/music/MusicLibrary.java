package net.transgressoft.commons.music;

import net.transgressoft.commons.music.audio.AudioItem;
import net.transgressoft.commons.music.playlist.AudioPlaylist;
import net.transgressoft.commons.music.playlist.AudioPlaylistDirectory;
import net.transgressoft.commons.music.waveform.AudioWaveform;
import net.transgressoft.commons.query.EntityEvent;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow.Subscriber;

public interface MusicLibrary<I extends AudioItem, P extends AudioPlaylist<I>, D extends AudioPlaylistDirectory<I>> extends Subscriber<EntityEvent<? extends I>> {

    Set<String> artists();

    short audioItemPlayCount(I audioItem);

    void deleteAudioItems(Set<I> audioItems);

    /**
     * Precondition, <tt>playlist</tt> exist in the <tt>AudioPlaylistRepository</tt>.
     * Otherwise, no action is performed.
     *
     * @param audioItems
     * @param playlist
     */
    void addAudioItemsToPlaylist(Collection<I> audioItems, P playlist);

    /**
     * Precondition, <tt>playlist</tt> exist in the <tt>AudioPlaylistRepository</tt>.
     * Otherwise, no action is performed.
     *
     * @param audioItems
     * @param playlist
     */
    void removeAudioItemsFromPlaylist(Collection<I> audioItems, P playlist);

    void movePlaylist(P playlist, D playlistDirectory);

    CompletableFuture<AudioWaveform> getOrCreateWaveformAsync(I audioItem, short width, short height);
}
