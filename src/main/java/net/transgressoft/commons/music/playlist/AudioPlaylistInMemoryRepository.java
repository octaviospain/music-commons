package net.transgressoft.commons.music.playlist;

import com.google.common.graph.GraphBuilder;
import com.google.common.graph.MutableGraph;
import net.transgressoft.commons.music.audio.AudioItem;
import net.transgressoft.commons.query.InMemoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;


public class AudioPlaylistInMemoryRepository extends InMemoryRepository<PlaylistNode<AudioItem>> implements AudioPlaylistRepository {

    private static final Logger LOG = LoggerFactory.getLogger(AudioPlaylistInMemoryRepository.class);

    private final MutableGraph<PlaylistNode<AudioItem>> graph = GraphBuilder.directed().build();

    public AudioPlaylistInMemoryRepository() {
        super(Collections.emptyList());
    }

    @Override
    public void add(PlaylistNode<AudioItem>... entities) throws RepositoryException {
        Objects.requireNonNull(entities);
        addAll(Set.of(entities));
    }

    @Override
    public void addAll(Set<PlaylistNode<AudioItem>> entities) throws RepositoryException {
        Objects.requireNonNull(entities);
        traverseDirectories(entities);
    }

    private void traverseDirectories(Collection<PlaylistNode<AudioItem>> playlistNodes) throws RepositoryException {
        for (PlaylistNode<AudioItem> p : playlistNodes) {
            graph.putEdge(p.getAncestor(), p);
            super.add(p);
            if (p instanceof AudioPlaylistDirectory) {
                AudioPlaylistDirectory apd = (AudioPlaylistDirectory) p;
                traverseDirectories(apd.descendantPlaylists());
            }
        }
    }

    @Override
    public void remove(PlaylistNode<AudioItem>... entities) {
        Objects.requireNonNull(entities);
        removeAll(Set.of(entities));
    }

    @Override
    public void removeAll(Set<PlaylistNode<AudioItem>> entities) {
        super.removeAll(entities);
        entities.forEach(e -> {
            var predecessors = graph.predecessors(e);
            predecessors.forEach(p -> p.removePlaylist(p));
            e.setAncestor(null);
            graph.removeNode(e);
        });
    }

    @Override
    public int size() {
        return graph.nodes().size() - 1;
    }

    @Override
    public List<PlaylistNode<AudioItem>> findAllByName(String name) {
        Objects.requireNonNull(name);
        return graph.nodes().stream().filter(p -> name.equals(p.getName())).collect(Collectors.toList());
    }

    @Override
    public Optional<AudioPlaylist> findSinglePlaylistByName(String name) throws RepositoryException {
        Objects.requireNonNull(name);

        var allByName = findAllByName(name);
        if (allByName.isEmpty()) {
            return Optional.empty();
        } else if (allByName.size() > 1) {
            throw new RepositoryException("Found several playlists when searching single by name " + name);
        } else if (allByName.get(0) instanceof AudioPlaylist) {
            return Optional.of((AudioPlaylist) allByName.get(0));
        } else {
            return Optional.empty();
        }
    }

    @Override
    public Optional<AudioPlaylistDirectory> findSingleDirectoryByName(String name) throws RepositoryException {
        Objects.requireNonNull(name);

        var allByName = findAllByName(name);
        if (allByName.isEmpty()) {
            return Optional.empty();
        } else if (allByName.size() > 1) {
            throw new RepositoryException("Found several playlists when searching single by name " + name);
        } else if (allByName.get(0) instanceof AudioPlaylistDirectory) {
            return Optional.of((AudioPlaylistDirectory) allByName.get(0));
        } else {
            return Optional.empty();
        }
    }

    @Override
    public <P extends PlaylistNode<AudioItem>> void movePlaylist(P playlistToMove, P destinationPlaylist) throws RepositoryException {
        Objects.requireNonNull(playlistToMove);
        Objects.requireNonNull(destinationPlaylist);
        if (! destinationPlaylist.isDirectory()) {
            throw new AudioPlaylistOperationException("Destination playlist is not a directory: " + destinationPlaylist);
        }

        if (findById(playlistToMove.id()).isEmpty()) {
            add(playlistToMove);
        }

        if (findById(destinationPlaylist.id()).isEmpty()) {
            add(destinationPlaylist);
        }

        graph.removeEdge(playlistToMove.getAncestor(), playlistToMove);
        graph.putEdge(destinationPlaylist, playlistToMove);
        playlistToMove.getAncestor().removePlaylist(playlistToMove);
        playlistToMove.setAncestor((AudioPlaylistDirectory) destinationPlaylist);
        destinationPlaylist.addPlaylist(playlistToMove);

        LOG.info("Playlist '{}' moved to '{}'", playlistToMove.getName(), destinationPlaylist.getName());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        AudioPlaylistInMemoryRepository that = (AudioPlaylistInMemoryRepository) o;
        return Objects.equals(graph, that.graph);
    }

    @Override
    public int hashCode() {
        return Objects.hash(graph);
    }
}
