package net.transgressoft.commons.music.playlist;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.UnmodifiableListIterator;
import net.transgressoft.commons.music.audio.AudioItem;
import net.transgressoft.commons.query.BooleanQueryTerm;
import net.transgressoft.commons.query.QueryEntity;
import net.transgressoft.commons.query.QueryPredicate;
import net.transgressoft.commons.query.attribute.EntityAttribute;
import net.transgressoft.commons.query.attribute.UnknownAttributeException;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;
import static net.transgressoft.commons.music.playlist.attribute.PlaylistNodeAttribute.ANCESTOR;
import static net.transgressoft.commons.music.playlist.attribute.PlaylistNodeAttribute.SELF;
import static net.transgressoft.commons.music.playlist.attribute.PlaylistStringAttribute.NAME;
import static net.transgressoft.commons.music.playlist.attribute.PlaylistStringAttribute.UNIQUE_ID;

/**
 * Base implementation of a {@code PlaylistNode}. All attributes are mutable but intend to be thread-safe, <tt>id</tt> is inmutable.
 *
 * @param <I> The type of the entities listed in the playlist node.
 */
public abstract class PlaylistNodeBase<I extends AudioItem> implements PlaylistNode<I> {

    private final int id;
    private final Set<PlaylistNode<I>> descendantPlaylists;
    private final Set<I> audioItems;
    private final Map<EntityAttribute<?>, Supplier<Object>> attributes;

    private String name;
    private PlaylistNode<I> ancestor;

    protected PlaylistNodeBase(int id, String name, PlaylistNode<I> ancestor, Set<PlaylistNode<I>> descendantPlaylists, List<I> audioItems) {
        requireNonNull(name);
        requireNonNull(ancestor);
        requireNonNull(descendantPlaylists);
        requireNonNull(audioItems);

        this.id = id;
        this.name = name;
        this.ancestor = ancestor;
        this.descendantPlaylists = new ConcurrentSkipListSet<>(descendantPlaylists);
        descendantPlaylists.forEach(p -> p.setAncestor(this));
        this.audioItems = new ConcurrentSkipListSet<>(audioItems);
        this.attributes = new ConcurrentHashMap<>();
        initializeAttributeMap();
    }

    // Only intended for the root audio playlist directory
    protected PlaylistNodeBase(int rootId) {
        id = rootId;
        name = "ROOT-" + getClass().getClassLoader().hashCode();
        descendantPlaylists = new ConcurrentSkipListSet<>(Collections.emptySet());
        audioItems = Collections.emptySet();
        attributes = new ConcurrentHashMap<>();
        ancestor = this;
        initializeAttributeMap();
    }

    private void initializeAttributeMap() {
        attributes.put(NAME, this::getName);
        attributes.put(UNIQUE_ID, this::getUniqueId);
        attributes.put(SELF, () -> this);
        attributes.put(ANCESTOR, this::getAncestor);
    }

    protected abstract void removeAncestor(PlaylistNode<I> p);

    @Override
    public int id() {
        return id;
    }

    @Override
    public String getUniqueId() {
        var stringJoiner = new StringJoiner("-")
                .add(String.valueOf(id()));
        if (isDirectory()) {
            stringJoiner.add("D");
        }
        return stringJoiner.add(getName()).toString();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(String name) {
        requireNonNull(name);
        this.name = name;
    }

    @Override
    public PlaylistNode<I> getAncestor() {
        return ancestor;
    }

    @Override
    public void setAncestor(PlaylistNode<I> ancestor) {
        requireNonNull(ancestor);
        this.ancestor = ancestor;
    }

    @Override
    public void addPlaylist(PlaylistNode<I> playlist) {
        if (playlist != null) {
            descendantPlaylists.add(playlist);
            playlist.setAncestor(this);
        }
    }

    @Override
    public void removePlaylist(PlaylistNode<I> playlist) {
        var iterator = descendantPlaylists.iterator();
        while (iterator.hasNext()) {
            var p = iterator.next();
            if (p.equals(playlist)) {
                removeAncestor(p);
                iterator.remove();
            }
        }
    }

    @Override
    public UnmodifiableListIterator<PlaylistNode<I>> descendantPlaylistsIterator() {
        return ImmutableList.copyOf(descendantPlaylists).listIterator();
    }

    @Override
    public void clearDescendantPlaylists() {
        descendantPlaylists.clear();
    }

    @Override
    public boolean isEmptyOfPlaylists() {
        return descendantPlaylists.isEmpty();
    }

    @Override
    public void addAudioItems(List<I> audioItems) {
        if (audioItems != null) {
            this.audioItems.addAll(audioItems);
        }
    }

    @Override
    public void removeAudioItems(Set<I> audioItems) {
        this.audioItems.removeAll(audioItems);
    }

    @Override
    public void removeAudioItems(I... audioItems) {
        removeAudioItems(Set.of(audioItems));
    }

    @Override
    public UnmodifiableListIterator<I> audioItemsListIterator() {
        return ImmutableList.copyOf(audioItems).listIterator();
    }

    @Override
    public void clearAudioItems() {
        audioItems.clear();
    }

    @Override
    public void clearAudioItemsFromPlaylists() {
        descendantPlaylists.forEach(PlaylistNode::clearAudioItems);
    }

    @Override
    public boolean isEmptyOfAudioItems() {
        return audioItems.isEmpty();
    }

    @Override
    public boolean audioItemsAllMatch(BooleanQueryTerm<AudioItem> queryPredicate) {
        return audioItems.stream()
                .allMatch(queryPredicate::apply);
    }

    @Override
    public boolean audioItemsAnyMatch(BooleanQueryTerm<AudioItem> queryPredicate) {
        return audioItems.stream()
                .anyMatch(queryPredicate::apply);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <A extends EntityAttribute<V>, V> V getAttribute(A attribute) throws UnknownAttributeException {
        return (V) Optional.ofNullable(attributes.get(attribute))
                .map(Supplier::get)
                .orElseThrow(() -> new UnknownAttributeException(attribute, getClass()));
    }

    @Override
    public int compareTo(@Nonnull QueryEntity object) {
        return Comparator.comparing(QueryEntity::getUniqueId, String.CASE_INSENSITIVE_ORDER).compare(this, object);
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlaylistNodeBase<I> that = (PlaylistNodeBase<I>) o;
        return Objects.equal(id(), that.id());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id());
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("id", id)
                .add("name", name)
                .add("ancestor", "{id=" + ancestor.id() + "}")
                .add("descendantPlaylists", descendantPlaylists.size())
                .add("audioItems", audioItems.size())
                .toString();
    }
}
