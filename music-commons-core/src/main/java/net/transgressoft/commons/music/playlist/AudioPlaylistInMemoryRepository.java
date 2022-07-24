package net.transgressoft.commons.music.playlist;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import net.transgressoft.commons.music.audio.AudioItem;
import net.transgressoft.commons.query.BooleanQueryTerm;
import net.transgressoft.commons.query.EntityAttribute;
import net.transgressoft.commons.query.InMemoryRepository;
import net.transgressoft.commons.query.RepositoryException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static net.transgressoft.commons.music.playlist.PlaylistStringAttribute.NAME;

public class AudioPlaylistInMemoryRepository<I extends AudioItem, P extends AudioPlaylist<I>, D extends AudioPlaylistDirectory<I>,
        MP extends MutableAudioPlaylist<I>, MD extends MutableAudioPlaylistDirectory<I>> implements AudioPlaylistRepository<I, P, D> {

    private static final Logger LOG = LoggerFactory.getLogger(AudioPlaylistInMemoryRepository.class);

    private final AtomicInteger idCounter = new AtomicInteger(1);
    private final Set<Integer> idSet = new HashSet<>();

    private final InMemoryRepository<MP> playlists;
    private final InMemoryRepository<MD> directories;
    private final Multimap<String, String> playlistsMultiMap = MultimapBuilder.treeKeys().treeSetValues().build();

    public AudioPlaylistInMemoryRepository() {
        this(new HashMap<>(), new HashMap<>());
    }

    protected AudioPlaylistInMemoryRepository(Map<Integer, MP> playlistsById, Map<Integer, MD> directoriesByID) {
      playlists = new InMemoryRepository<>(playlistsById);
      directories = new InMemoryRepository<>(directoriesByID);
    }

    @Override
    public void add(P... playlists) {
        Objects.requireNonNull(playlists);
        addAll(Set.of(playlists));
    }

    @Override
    public void addAll(Set<P> playlists) {
        Objects.requireNonNull(playlists);
        playlists.forEach(this::add);
    }

    private void add(P playlist) {
        if (playlist.isDirectory()) {
            if (directories.findByAttribute(NAME, playlist.getName()).isEmpty()) {
                var mutableDirectory = toMutableDirectory((D) playlist);
                directories.add(mutableDirectory);
                addRecursive(mutableDirectory, mutableDirectory.descendantPlaylists());
            }
        } else {
            if (playlists.findByAttribute(NAME, playlist.getName()).isEmpty()) {
                playlists.add(toMutablePlaylists(playlist));
            }
        }
    }

    protected int getNewId() {
        int id;
        do {
            id = idCounter.getAndIncrement();
        } while (idSet.contains(id));
        idSet.add(id);
        return id;
    }

    private MP toMutablePlaylists(P playlistDirectory) {
        return (MP) new MutablePlaylist<>(getNewId(), playlistDirectory.getName(), playlistDirectory.audioItems());
    }

    private MD toMutableDirectory(D playlistDirectory) {
        return (MD) new MutablePlaylistDirectory<>(getNewId(), playlistDirectory.getName(), playlistDirectory.audioItems(), toMutablePlaylists(playlistDirectory.descendantPlaylists()));
    }

    private void addRecursive(MutableAudioPlaylistDirectory<I> parent, Set<MutableAudioPlaylist<I>> mutablePlaylistNodes) {
        for (MutableAudioPlaylist<I> playlist : mutablePlaylistNodes) {
            playlistsMultiMap.put(parent.getUniqueId(), playlist.getUniqueId());
            add((P) playlist);
        }
    }

    private MD findDirectory(int id) {
        return directories.findById(id).orElseThrow(() -> new RuntimeException("Playlist Directory not found by id: " + id));
    }

    @Override
    public void remove(P... entities) {
        Objects.requireNonNull(entities);
        removeAll(Set.of(entities));
    }

    @Override
    public void removeAll(Set<P> entities) {
        Objects.requireNonNull(entities);
        removeRecursive(entities);
    }

    @Override
    public void clear() {
        playlists.clear();
        directories.clear();
        playlistsMultiMap.clear();
    }

    private void removeRecursive(Set<P> mutablePlaylistNodes) {
        for (P p : mutablePlaylistNodes) {
            playlistsMultiMap.asMap().remove(p.getUniqueId());
            if (p.isDirectory()) {
                removeRecursive(findDirectory(p.id()).descendantPlaylists());
            }
            directories.findById(p.id()).ifPresent(directories::remove);
            playlists.findById(p.id()).ifPresent(playlists::remove);
        }
    }

    @Override
    public List<P> search(BooleanQueryTerm<P> booleanQueryTerm) {
        return ImmutableList.<P>builder()
                .addAll(playlists.search((BooleanQueryTerm<MP>) booleanQueryTerm).stream()
                                .map(this::toImmutablePlaylist)
                                .toList())
                .addAll(directories.search((BooleanQueryTerm<MD>) booleanQueryTerm).stream()
                                .map(this::toImmutablePlaylistDirectory)
                                .toList()).build();
    }

    @Override
    public Optional<P> findById(int id) {
        return findByIdInternal(id).map(this::toImmutablePlaylist);
    }

    private Optional<MP> findByIdInternal(int id) {
        return playlists.findById(id).or(() -> (Optional<? extends MP>) directories.findById(id));
    }

    @Override
    public Optional<P> findByUniqueId(String id) {
        return playlists.findByUniqueId(id)
                .map(this::toImmutablePlaylist)
                .or(() -> directories.findByUniqueId(id).map(this::toImmutablePlaylistDirectory));
    }

    @Override
    public <A extends EntityAttribute<V>, V> List<P> findByAttribute(A a, V v) {
        return ImmutableList.<P>builder()
                .addAll(playlists.findByAttribute(a, v).stream()
                                .map(this::toImmutablePlaylist)
                                .toList())
                .addAll(directories.findByAttribute(a, v).stream()
                                .map(this::toImmutablePlaylistDirectory)
                                .toList()).build();
    }

    @Override
    public <A extends EntityAttribute<V>, V> Optional<P> findSingleByAttribute(A a, V v) throws RepositoryException {
        var foundPlaylist = playlists.findSingleByAttribute(a, v).orElse(null);
        var foundDirectory = directories.findSingleByAttribute(a, v).orElse(null);
        if (foundPlaylist != null && foundDirectory != null)
            throw new RepositoryException("Found 2 entities with the same attribute: [" + foundPlaylist + ", " + foundDirectory + "]");

        return foundPlaylist != null ?
                Optional.of(toImmutablePlaylist(foundPlaylist)) : Optional.ofNullable(toImmutablePlaylistDirectory(foundDirectory));
    }

    @Override
    public int size() {
        return playlists.size() + directories.size();
    }

    @Override
    public boolean isEmpty() {
        return playlists.isEmpty() && directories.isEmpty();
    }

    @Override
    @SuppressWarnings("UnstableApiUsage")
    public Iterator<P> iterator() {
        var setBuilder = ImmutableSet.<P>builderWithExpectedSize(playlists.size() + directories.size());
        playlists.forEach(p -> setBuilder.add(toImmutablePlaylist(p)));
        directories.forEach(d -> setBuilder.add(toImmutablePlaylistDirectory(d)));
        return setBuilder.build().iterator();
    }

    private P toImmutablePlaylist(MutableAudioPlaylist<I> audioPlaylist) {
        return (P) new ImmutablePlaylist<>(audioPlaylist.id(), audioPlaylist.getName(), audioPlaylist.audioItems());
    }

    private P toImmutablePlaylist(P audioPlaylist) {
        return (P) new ImmutablePlaylist<>(audioPlaylist.id(), audioPlaylist.getName(), audioPlaylist.audioItems());
    }

    private P toImmutablePlaylistDirectory(MutableAudioPlaylistDirectory<I> playlistDirectory) {
        return playlistDirectory == null ? null :
                (P) new ImmutablePlaylistDirectory<>(playlistDirectory.id(), playlistDirectory.getName(), playlistDirectory.audioItems(),
                                                     toImmutablePlaylistDirectories(playlistDirectory.descendantPlaylists()));
    }

    private P toImmutablePlaylistDirectory(D playlistDirectory) {
        return playlistDirectory == null ? null :
                (P) new ImmutablePlaylistDirectory<>(playlistDirectory.id(), playlistDirectory.getName(), playlistDirectory.audioItems(),
                                                     toImmutablePlaylistDirectories(playlistDirectory.descendantPlaylists()));
    }

    private Set<P> toImmutablePlaylistDirectories(Set<P> audioPlaylists) {
        return audioPlaylists.stream()
                .map(p -> p.isDirectory() ? toImmutablePlaylistDirectory((D) p) : toImmutablePlaylist(p))
                .collect(Collectors.toSet());
    }

    @Override
    public int numberOfPlaylists() {
        return playlists.size();
    }

    @Override
    public int numberOfPlaylistDirectories() {
        return directories.size();
    }

    @Override
    public P createPlaylist(String name) throws RepositoryException {
        return createPlaylist(name, Collections.emptyList());
    }

    @Override
    public P createPlaylist(String name, List<I> audioItems) throws RepositoryException {
        var searchResult = findSinglePlaylistByName(name);
        if (searchResult.isEmpty()) {
            var playlist = (MP) new MutablePlaylist<>(getNewId(), name, audioItems);
            playlists.add(playlist);
            return toImmutablePlaylist(playlist);
        } else {
            throw new RepositoryException("Playlist with name '" + name + "' already exists");
        }
    }

    @Override
    public D createPlaylistDirectory(String name) throws RepositoryException {
        return createPlaylistDirectory(name, Collections.emptyList());
    }

    @Override
    public D createPlaylistDirectory(String name, List<I> audioItems) throws RepositoryException {
        var searchResult = findSingleDirectoryByName(name);
        if (searchResult.isEmpty()) {
            var playlistDirectory = (MD) new MutablePlaylistDirectory<>(getNewId(), name, audioItems);
            directories.add(playlistDirectory);
            return (D) toImmutablePlaylistDirectory(playlistDirectory);
        } else {
            throw new RepositoryException("Playlist with name '" + name + "' already exists");
        }
    }

    @Override
    public void addAudioItemsToPlaylist(List<I> audioItems, P playlist) {
        Objects.requireNonNull(audioItems);
        Objects.requireNonNull(playlist);

        playlists.findById(playlist.id())
                .or(() -> (Optional<? extends MP>) directories.findById(playlist.id()))
                .ifPresent(p -> p.addAudioItems(audioItems));
    }

    @Override
    public void addPlaylistsToDirectory(Set<P> playlistsToAdd, D directory) {
        Objects.requireNonNull(playlistsToAdd);
        Objects.requireNonNull(directory);

        var mutablePlaylists = toMutablePlaylists(playlistsToAdd);

        directories.findById(directory.id())
                .ifPresent(d -> {
                    d.addPlaylists(mutablePlaylists);
                    playlistsMultiMap.putAll(d.getUniqueId(), mutablePlaylists.stream().map(MutableAudioPlaylist::getUniqueId).collect(Collectors.toSet()));
                });
    }

    private Set<MutableAudioPlaylist<I>> toMutablePlaylists(Set<P> audioPlaylists) {
        return audioPlaylists.stream()
                .map(e -> {
                    if (e.isDirectory()) {
                        AudioPlaylistDirectory<I> dir = (AudioPlaylistDirectory<I>) e;
                        return new MutablePlaylistDirectory<>(dir.id(), dir.getName(), dir.audioItems(), dir.descendantPlaylists());
                    } else {
                        return new MutablePlaylist<>(e.id(), e.getName(), e.audioItems());
                    }
                })
                .collect(Collectors.toSet());
    }

    @Override
    public void movePlaylist(P playlistToMove, D destinationPlaylist) {
        Objects.requireNonNull(playlistToMove);
        Objects.requireNonNull(destinationPlaylist);

        findByIdInternal(playlistToMove.id())
                .ifPresent(playlist -> directories.findById(destinationPlaylist.id()).ifPresent(playlistDirectory -> {
                    ancestor(playlistToMove).ifPresent(ancestor -> {
                        playlistsMultiMap.remove(ancestor.getUniqueId(), playlist.getUniqueId());
                        ancestor.removePlaylists(playlist);
                    });
                    playlistsMultiMap.put(playlistDirectory.getUniqueId(), playlist.getUniqueId());
                    playlistDirectory.addPlaylists(playlist);

                    LOG.debug("Playlist '{}' moved to '{}'", playlistToMove.getName(), destinationPlaylist.getName());
                }));
    }

    private Optional<MD> ancestor(P playlistNode) {
        if (playlistsMultiMap.containsValue(playlistNode.getUniqueId())) {
            return playlistsMultiMap.entries().stream()
                    .filter(entry -> playlistNode.getUniqueId().equals(entry.getValue()))
                    .map(entry -> directories.findByUniqueId(entry.getKey()).get())
                    .findFirst();
        } else {
            return Optional.empty();
        }
    }

    @Override
    public List<P> findAllByName(String name) {
        Objects.requireNonNull(name);
        return ImmutableList.<P>builder()
                .addAll(playlists.findByAttribute(NAME, name).stream()
                                .map(this::toImmutablePlaylist)
                                .collect(Collectors.toSet()))
                .addAll(directories.findByAttribute(NAME, name).stream()
                                .map(this::toImmutablePlaylistDirectory)
                                .collect(Collectors.toSet())).build();
    }

    @Override
    public Optional<P> findSinglePlaylistByName(String name) {
        Objects.requireNonNull(name);
        try {
            return playlists.findSingleByAttribute(NAME, name).map(this::toImmutablePlaylist);
        }
        catch (RepositoryException exception) {
            throw new IllegalStateException(exception);
        }
    }

    @Override
    public Optional<D> findSingleDirectoryByName(String name) {
        Objects.requireNonNull(name);
        try {
            return directories.findSingleByAttribute(NAME, name).map(playlistDirectory -> (D) toImmutablePlaylistDirectory(playlistDirectory));
        }
        catch (RepositoryException exception) {
            throw new IllegalStateException(exception);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        var that = (AudioPlaylistInMemoryRepository<I, P, D, MP, MD>) o;
        return Objects.equals(playlistsMultiMap, that.playlistsMultiMap) && Objects.equals(playlists, that.playlists) && Objects.equals(directories, that.directories);
    }

    @Override
    public int hashCode() {
        return Objects.hash(playlistsMultiMap, playlists, directories);
    }
}
