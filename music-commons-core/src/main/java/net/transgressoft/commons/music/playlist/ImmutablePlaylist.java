package net.transgressoft.commons.music.playlist;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import net.transgressoft.commons.music.audio.AudioItem;
import net.transgressoft.commons.query.BooleanQueryTerm;
import net.transgressoft.commons.query.EntityAttribute;
import net.transgressoft.commons.query.QueryEntity;

import javax.annotation.Nonnull;
import java.util.Collection;
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
import static net.transgressoft.commons.music.playlist.PlaylistNodeAttribute.SELF;
import static net.transgressoft.commons.music.playlist.PlaylistStringAttribute.NAME;
import static net.transgressoft.commons.music.playlist.PlaylistStringAttribute.UNIQUE_ID;

class ImmutablePlaylist<I extends AudioItem> implements AudioPlaylist<I> {

    private final int id;
    private final Set<I> audioItems;
    private final Map<EntityAttribute<?>, Supplier<Object>> attributes;
    private String name;

    protected ImmutablePlaylist(int id, String name, List<I> audioItems) {
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
    }

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

    protected void setName(String name) {
        this.name = name;
    }

    @Override
    public List<I> audioItems() {
        return ImmutableList.copyOf(audioItems);
    }

    @Override
    public boolean isDirectory() {
        return false;
    }

    protected void addAll(List<I> audioItems) {
        this.audioItems.addAll(audioItems);
    }

    protected void removeAll(Collection<I> audioItems) {
        audioItems.forEach(this.audioItems::remove);
    }

    protected void clear() {
        this.audioItems.clear();
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


    @Override
    public <A extends EntityAttribute<V>, V> V getAttribute(A attribute) throws UnknownAttributeException {
        return (V) Optional.ofNullable(attributes.get(attribute))
                .map(Supplier::get)
                .orElseThrow(() -> new UnknownAttributeException(attribute, getClass()));
    }

    @Override
    public int compareTo(@Nonnull AudioPlaylist<I> object) {
        return Comparator.comparing(QueryEntity::getUniqueId, String.CASE_INSENSITIVE_ORDER).compare(this, object);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        var that = (ImmutablePlaylist<I>) o;
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
                .add("audioItems", audioItems.size())
                .omitNullValues()
                .toString();
    }
}
