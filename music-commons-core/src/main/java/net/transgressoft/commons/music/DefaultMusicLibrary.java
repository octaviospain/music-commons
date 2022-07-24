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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Flow.Subscription;

public class DefaultMusicLibrary<I extends AudioItem, P extends AudioPlaylist<I>, D extends AudioPlaylistDirectory<I>, W extends AudioWaveform>
        implements MusicLibrary<I, P, D> {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultMusicLibrary.class);

    private final AudioPlaylistRepository<I, P, D> audioPlaylistRepository;
    private final AudioItemRepository<I> audioItemRepository;
    private final Repository<W> waveformRepository;

    private final Set<String> artists = new HashSet<>();

    private Subscription audioItemSubscription;

    public DefaultMusicLibrary(AudioItemRepository<I> audioItemRepository,
                               AudioPlaylistRepository<I, P, D> audioPlaylistRepository,
                               Repository<W> waveformRepository) {
        Objects.requireNonNull(audioItemRepository);
        Objects.requireNonNull(audioPlaylistRepository);
        Objects.requireNonNull(waveformRepository);

        this.audioItemRepository = audioItemRepository;
        this.audioPlaylistRepository = audioPlaylistRepository;
        this.waveformRepository = waveformRepository;

        audioItemRepository.iterator().forEachRemaining(audioItem -> artists.addAll(audioItem.artistsInvolved()));
    }

    @Override
    public Set<String> artists() {
        return artists;
    }

    @Override
    public void deleteAudioItems(Set<I> audioItems) {
        audioItemRepository.removeAll(audioItems);
        audioPlaylistRepository.removeAudioItems(audioItems);
        audioItems.stream()
                .map(AudioItem::getId)
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
    public CompletableFuture<AudioWaveform> getOrCreateWaveformAsync(I audioItem, short width, short height) {
        return waveformRepository.findById(audioItem.getId())
                .<CompletableFuture<AudioWaveform>>map(CompletableFuture::completedFuture)
                .orElseGet(() -> CompletableFuture.supplyAsync(() -> {
                    try {
                        return ImmutableAudioWaveform.create(audioItem.getId(), audioItem.path(), width, height);
                    }
                    catch (AudioWaveformProcessingException exception) {
                        throw new CompletionException(exception);
                    }
                }));
    }
}
