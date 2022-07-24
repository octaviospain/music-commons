package net.transgressoft.commons.music.playlist;

import com.google.common.graph.ElementOrder;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.MutableGraph;
import net.transgressoft.commons.music.audio.AudioItem;
import net.transgressoft.commons.query.BooleanQueryTerm;
import net.transgressoft.commons.query.EntityAttribute;
import net.transgressoft.commons.query.InMemoryRepository;
import net.transgressoft.commons.query.RepositoryException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class AudioPlaylistInMemoryRepository extends InMemoryRepository<MutablePlaylistNode<AudioItem>> implements AudioPlaylistRepository {

    private static final Logger LOG = LoggerFactory.getLogger(AudioPlaylistInMemoryRepository.class);
    private static final AtomicInteger idCounter = new AtomicInteger(1);    // 0 is for RootAudioPlaylistNode

    @SuppressWarnings("UnstableApiUsage")
    private final MutableGraph<MutablePlaylistNode<AudioItem>> graph = GraphBuilder.undirected()
            .nodeOrder(ElementOrder.sorted(Comparator.comparingInt((MutablePlaylistNode<AudioItem> n) -> n.id())))
            .build();

    public AudioPlaylistInMemoryRepository() {
        super(Collections.emptyList());
    }

    @Override
    public AudioPlaylistBuilder<MutableAudioPlaylist<AudioItem>, AudioItem> createPlaylist(String name) {
        return new MutableAudioPlaylistBuilder(idCounter.getAndIncrement(), name);
    }

    @Override
    public AudioPlaylistDirectoryBuilder<MutablePlaylistDirectory<AudioItem>, AudioItem> createPlaylistDirectory(String name) {
        return new MutableAudioPlaylistDirectoryBuilder(idCounter.getAndIncrement(), name);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void add(MutablePlaylistNode<AudioItem>... entities) throws RepositoryException {
        Objects.requireNonNull(entities);
        addAll(Set.of(entities));
    }

    @Override
    public void addAll(Set<MutablePlaylistNode<AudioItem>> entities) throws RepositoryException {
        Objects.requireNonNull(entities);
        addRecursive(entities.iterator());
    }

    @SuppressWarnings({"unchecked", "UnstableApiUsage"})
    private void addRecursive(Iterator<MutablePlaylistNode<AudioItem>> mutablePlaylistNodes) throws RepositoryException {
        while (mutablePlaylistNodes.hasNext()) {
            MutablePlaylistNode<AudioItem> p = mutablePlaylistNodes.next();
            graph.putEdge(p.getAncestor(), p);
            super.add(p);
            if (p instanceof MutablePlaylistDirectory<AudioItem> apd) {
                addRecursive(apd.descendantPlaylistsIterator());
            }
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void remove(MutablePlaylistNode<AudioItem>... entities) {
        Objects.requireNonNull(entities);
        removeAll(Set.of(entities));
    }

    @Override
    public void removeAll(Set<MutablePlaylistNode<AudioItem>> entities) {
        Objects.requireNonNull(entities);
        removeRecursive(entities.iterator());
    }

    @SuppressWarnings({"unchecked", "UnstableApiUsage"})
    private void removeRecursive(Iterator<MutablePlaylistNode<AudioItem>> mutablePlaylistNodes) {
        while (mutablePlaylistNodes.hasNext()) {
            MutablePlaylistNode<AudioItem> p = mutablePlaylistNodes.next();
            graph.removeNode(p);
            super.remove(p);
            p.getAncestor().removePlaylist(p);
            p.setAncestor(null);

            if (p instanceof MutablePlaylistDirectory<AudioItem> apd) {
                removeRecursive(apd.descendantPlaylistsIterator());
            }
        }
    }

    @Override
    public List<MutablePlaylistNode<AudioItem>> search(BooleanQueryTerm<MutablePlaylistNode<AudioItem>> query) {
        var result = new ArrayList<>(super.search(query));
        result.remove(RootAudioPlaylistNode.INSTANCE);
        return result;
    }


    @Override
    public Optional<MutablePlaylistNode<AudioItem>> findByUniqueId(String uniqueId) {
        var result = super.findByUniqueId(uniqueId);
        return result.flatMap(o -> {
            if (RootAudioPlaylistNode.INSTANCE.equals(o))
                return Optional.empty();
            else
                return Optional.of(o);
        });
    }

    @Override
    public <A extends EntityAttribute<V>, V> List<MutablePlaylistNode<AudioItem>> findByAttribute(A attribute, V value) {
        var result = super.findByAttribute(attribute, value);
        result.remove(RootAudioPlaylistNode.INSTANCE);
        return result;
    }

    @Override
    public int size() {
        return (int) graph.nodes().stream().filter(n -> !RootAudioPlaylistNode.INSTANCE.equals(n)).count();
    }

    @Override
    @SuppressWarnings("UnstableApiUsage")
    public List<MutablePlaylistNode<AudioItem>> findAllByName(String name) {
        Objects.requireNonNull(name);
        return graph.nodes().stream().filter(p -> name.equals(p.getName())).collect(Collectors.toList());
    }

    @Override
    public Optional<MutableAudioPlaylist<AudioItem>> findSinglePlaylistByName(String name) throws RepositoryException {
        Objects.requireNonNull(name);

        var allByName = findAllByName(name);
        if (allByName.isEmpty()) {
            return Optional.empty();
        } else if (allByName.size() > 1) {
            throw new RepositoryException("Found several playlists when searching single by name " + name);
        } else if (allByName.get(0) instanceof DefaultMutableAudioPlaylist d) {
            return Optional.of(d);
        } else {
            return Optional.empty();
        }
    }

    @Override
    public Optional<MutablePlaylistDirectory<AudioItem>> findSingleDirectoryByName(String name) throws RepositoryException {
        Objects.requireNonNull(name);

        var allByName = findAllByName(name);
        if (allByName.isEmpty()) {
            return Optional.empty();
        } else if (allByName.size() > 1) {
            throw new RepositoryException("Found several playlists when searching single by name " + name + ": " + allByName);
        } else if (allByName.get(0) instanceof DefaultMutableAudioPlaylistDirectory d) {
            return Optional.of(d);
        } else {
            return Optional.empty();
        }
    }

    @Override
    @SuppressWarnings({"unchecked", "UnstableApiUsage"})
    public <P extends MutablePlaylistNode<AudioItem>, D extends MutablePlaylistDirectory<AudioItem>> void movePlaylist(P playlistToMove, D destinationPlaylist) throws RepositoryException {
        Objects.requireNonNull(playlistToMove);
        Objects.requireNonNull(destinationPlaylist);

        if (findById(playlistToMove.id()).isEmpty()) {
            add(playlistToMove);
        }

        if (findById(destinationPlaylist.id()).isEmpty()) {
            add(destinationPlaylist);
        }

        graph.removeEdge(playlistToMove.getAncestor(), playlistToMove);
        graph.putEdge(destinationPlaylist, playlistToMove);
        playlistToMove.getAncestor().removePlaylist(playlistToMove);
        playlistToMove.setAncestor(destinationPlaylist);
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
