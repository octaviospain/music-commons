package net.transgressoft.commons.music.playlist;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.UnmodifiableListIterator;
import net.transgressoft.commons.music.audio.AudioItem;
import net.transgressoft.commons.query.BooleanQueryTerm;
import net.transgressoft.commons.query.EntityAttribute;
import net.transgressoft.commons.query.QueryEntity;

import javax.annotation.Nonnull;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;
import static net.transgressoft.commons.music.playlist.PlaylistNodeAttribute.ANCESTOR;
import static net.transgressoft.commons.music.playlist.PlaylistNodeAttribute.SELF;
import static net.transgressoft.commons.music.playlist.PlaylistStringAttribute.NAME;
import static net.transgressoft.commons.music.playlist.PlaylistStringAttribute.UNIQUE_ID;

/**
 * Base implementation of a {@code PlaylistNode}. All attributes are mutable but intend to be thread-safe, <tt>id</tt> is inmutable.
 *
 * @param <I> The type of the entities listed in the playlist node.
 */
abstract class MutablePlaylistNodeBase<I extends AudioItem> implements MutablePlaylistNode<I> {

    private final int id;
    private final Set<I> audioItems;
    private final Map<EntityAttribute<?>, Supplier<Object>> attributes;
    private MutablePlaylistDirectory<I> ancestor;

    private String name;

    protected MutablePlaylistNodeBase(int id, String name, MutablePlaylistDirectory<I> ancestor, List<I> audioItems) {
        requireNonNull(name);
        requireNonNull(ancestor);
        requireNonNull(audioItems);

        this.id = id;
        this.ancestor = ancestor;
        this.name = name;

        this.audioItems = new ConcurrentSkipListSet<>(audioItems);
        this.attributes = new ConcurrentHashMap<>();
        initializeAttributeMap();
    }

    // Only for NullAudioPlaylistNode
    MutablePlaylistNodeBase(int id, String name, List<I> audioItems) {
        requireNonNull(name);
        requireNonNull(audioItems);

        this.id = id;
        this.name = name;

        this.audioItems = new ConcurrentSkipListSet<>(audioItems);
        this.attributes = new ConcurrentHashMap<>();
        initializeAttributeMap();
    }

    private void initializeAttributeMap() {
        attributes.put(NAME, this::getName);
        attributes.put(UNIQUE_ID, this::getUniqueId);
        attributes.put(SELF, () -> this);
        attributes.put(ANCESTOR, this::getAncestor);
    }

    @Override
    public int id() {
        return id;
    }

    @Override
    public MutablePlaylistDirectory<I> getAncestor() {
        return ancestor;
    }

    @Override
    public void setAncestor(MutablePlaylistDirectory<I> ancestor) {
        this.ancestor = ancestor;
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
    public void addAudioItems(List<I> audioItems) {
        requireNonNull(audioItems);
        this.audioItems.addAll(audioItems);
    }

    @Override
    public void removeAudioItems(Set<I> audioItems) {
        requireNonNull(audioItems);
        this.audioItems.removeAll(audioItems);
    }

    @Override
    public int numberOfAudioItems() {
        return this.audioItems.size();
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
    public boolean isEmptyOfAudioItems() {
        return audioItems.isEmpty();
    }

    @Override
    public boolean audioItemsAllMatch(BooleanQueryTerm<AudioItem> queryPredicate) {
        requireNonNull(queryPredicate);
        return audioItems.stream()
                .allMatch(queryPredicate::apply);
    }

    @Override
    public boolean audioItemsAnyMatch(BooleanQueryTerm<AudioItem> queryPredicate) {
        requireNonNull(queryPredicate);
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
        MutablePlaylistNodeBase<I> that = (MutablePlaylistNodeBase<I>) o;
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
                .add("ancestor", "{id=" + getAncestor().id() + "}")
                .add("audioItems", audioItems.size())
                .toString();
    }
}
