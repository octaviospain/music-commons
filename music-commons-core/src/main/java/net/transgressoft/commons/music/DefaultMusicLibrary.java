package net.transgressoft.commons.music;

import net.transgressoft.commons.music.audio.AudioItem;
import net.transgressoft.commons.music.audio.AudioItemRepository;
import net.transgressoft.commons.music.playlist.AudioPlaylist;
import net.transgressoft.commons.music.playlist.AudioPlaylistDirectory;
import net.transgressoft.commons.music.playlist.AudioPlaylistRepository;
import net.transgressoft.commons.music.waveform.AudioWaveform;
import net.transgressoft.commons.music.waveform.AudioWaveformProcessingException;
import net.transgressoft.commons.music.waveform.ImmutableAudioWaveform;
import net.transgressoft.commons.query.Repository;

import java.util.Collection;
import java.util.Iterator;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public class DefaultMusicLibrary<I extends AudioItem, P extends AudioPlaylist<I>, D extends AudioPlaylistDirectory<I>, W extends AudioWaveform>
        implements MusicLibrary<I, P, D> {

    private final AudioItemRepository<I> audioItemRepository;
    private final AudioPlaylistRepository<I, P, D> audioPlaylistRepository;
    private final Repository<W> waveformRepository;

    public DefaultMusicLibrary(AudioItemRepository<I> audioItemRepository,
                               AudioPlaylistRepository<I, P, D> audioPlaylistRepository,
                               Repository<W> waveformRepository) {
        this.audioItemRepository = audioItemRepository;
        this.audioPlaylistRepository = audioPlaylistRepository;
        this.waveformRepository = waveformRepository;
    }

    @Override
    public Iterator<I> audioItems() {
        return audioItemRepository.iterator();
    }

    @Override
    public void deleteAudioItems(Set<I> audioItems) {
        audioItemRepository.removeAll(audioItems);
        audioPlaylistRepository.removeAudioItems(audioItems);
        audioItems.stream()
                .map(AudioItem::id)
                .map(waveformRepository::findById)
                .filter(Optional::isPresent)
                .forEach(waveform -> waveformRepository.remove(waveform.get()));
    }

    @Override
    public void addAudioItemsToPlaylist(Collection<I> audioItems, P playlist) {
        audioPlaylistRepository.addAudioItemsToPlaylist(audioItems, playlist);
    }

    @Override
    public void removeAudioItemsFromPlaylist(Collection<I> audioItems, P playlist) {
        audioPlaylistRepository.removeAudioItemsFromPlaylist(audioItems, playlist);
    }

    @Override
    public void movePlaylist(P playlist, D playlistDirectory) {
        audioPlaylistRepository.movePlaylist(playlist, playlistDirectory);
    }

    @Override
    public CompletableFuture<AudioWaveform> getOrCreateWaveformAsync(I audioItem, short width, short height) throws AudioWaveformProcessingException {
        return waveformRepository.findById(audioItem.id())
                .<CompletableFuture<AudioWaveform>>map(CompletableFuture::completedFuture)
                .orElseGet(() -> CompletableFuture.supplyAsync(() -> {
                    try {
                        return ImmutableAudioWaveform.create(audioItem.id(), audioItem.path(), width, height);
                    }
                    catch (AudioWaveformProcessingException exception) {
                        throw new CompletionException(exception);
                    }
                }));
    }
}
