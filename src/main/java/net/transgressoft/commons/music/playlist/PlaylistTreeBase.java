package net.transgressoft.commons.music.playlist;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableSet;
import net.transgressoft.commons.music.audio.AudioItem;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Base implementation of {@link PlaylistTree<I>} interface with generic methods for interface operations.
 *
 * @author Octavio Calleya
 */
public abstract class PlaylistTreeBase<I extends AudioItem> implements PlaylistTree<I> {

    private final Set<AudioPlaylist<I>> audioPlaylists;
    private final Set<PlaylistTree<I>> includedPlaylistTrees;
    private final String name;

    protected PlaylistTreeBase(String name, Set<PlaylistTree<I>> includedPlaylistTrees, Set<AudioPlaylist<I>> audioPlaylists) {
        this.name = name;
        this.includedPlaylistTrees = new HashSet<>(includedPlaylistTrees);
        this.audioPlaylists = new HashSet<>(audioPlaylists);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public ImmutableSet<AudioPlaylist<I>> audioPlaylists() {
        return ImmutableSet.copyOf(audioPlaylists);
    }

    @Override
    public ImmutableSet<PlaylistTree<I>> includedPlaylistTrees() {
        return ImmutableSet.copyOf(includedPlaylistTrees);
    }

    @Override
    public Optional<PlaylistTree<I>> findParentPlaylist(String playlistName) {
        Optional<AudioPlaylist<I>> foundPlaylist = audioPlaylists.stream()
                .filter(playlist -> playlist.name().equals(playlistName))
                .findAny();

        if (foundPlaylist.isPresent())
            return Optional.of(this);

        Optional<PlaylistTree<I>> foundPlaylistTree = includedPlaylistTrees.stream()
                .filter(playlistTree -> playlistTree.name().equals(playlistName))
                .findAny();

        if (foundPlaylistTree.isPresent())
            return Optional.of(this);

        for (PlaylistTree<I> subPlaylistTree : includedPlaylistTrees) {
            Optional<PlaylistTree<I>> result = subPlaylistTree.findParentPlaylist(playlistName);
            if (result.isPresent())
                return result;
        }

        return Optional.empty();
    }

    @Override
    public Optional<AudioPlaylist<I>> findAudioPlaylistByName(String playlistName) {
        Optional<AudioPlaylist<I>> result = audioPlaylists.stream()
                .filter(playlist -> playlist.name().equals(playlistName))
                .findAny();

        if (!result.isPresent()) {
            for (PlaylistTree<I> subPlaylistTree : includedPlaylistTrees) {
                result = subPlaylistTree.findAudioPlaylistByName(playlistName);
                if (result.isPresent())
                    return result;
            }
            return Optional.empty();
        } else
            return result;
    }

    @Override
    public Optional<PlaylistTree<I>> findPlaylistTreeByName(String playlistTreeName) {
        Optional<PlaylistTree<I>> result = includedPlaylistTrees.stream()
                .filter(playlist -> playlist.name().equals(playlistTreeName))
                .findAny();

        if (!result.isPresent()) {
            for (PlaylistTree<I> subPlaylistTree : includedPlaylistTrees) {
                result = subPlaylistTree.findPlaylistTreeByName(playlistTreeName);
                if (result.isPresent())
                    return result;
            }
            return Optional.empty();
        } else
            return result;
    }

    @Override
    public ImmutableSet<I> audioItems() {
        Set<I> itemsFromPlaylists = audioPlaylists.stream()
                .flatMap(playlist -> playlist.audioItems().stream())
                .collect(Collectors.toSet());

        Set<I> itemsFromSubPlaylistTrees = includedPlaylistTrees.stream()
                .flatMap(playlistTree -> playlistTree.audioItems().stream())
                .collect(Collectors.toSet());

        return ImmutableSet.<I>builder()
                .addAll(itemsFromPlaylists)
                .addAll(itemsFromSubPlaylistTrees)
                .build();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlaylistTreeBase<?> that = (PlaylistTreeBase<?>) o;
        return Objects.equal(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(name);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("audioPlaylists", audioPlaylists)
                .add("includedPlaylistTrees", includedPlaylistTrees)
                .add("name", name)
                .toString();
    }
}
