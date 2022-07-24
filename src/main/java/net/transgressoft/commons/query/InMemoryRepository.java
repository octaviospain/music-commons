package net.transgressoft.commons.query;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.UnmodifiableListIterator;
import net.transgressoft.commons.music.playlist.AudioPlaylistDirectory;
import net.transgressoft.commons.music.playlist.RepositoryException;
import net.transgressoft.commons.query.attribute.EntityAttribute;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toConcurrentMap;
import static java.util.stream.Collectors.toMap;

public class InMemoryRepository<E extends QueryEntity> implements Repository<E> {

    private final ConcurrentMap<Integer, E> entitiesById;

    public InMemoryRepository() {
        this(Collections.emptyList());
    }

    public InMemoryRepository(Collection<E> entities) {
        Objects.requireNonNull(entities);
        entitiesById = entities.stream().collect(toConcurrentMap(E::id, Function.identity()));
    }

    @Override
    public void add(E... entities) throws RepositoryException {
        Objects.requireNonNull(entities);
        for (E entity : entities) {
            entitiesById.put(entity.id(), entity);
        }
    }

    @Override
    public void addAll(Set<E> entities) throws RepositoryException {
        Objects.requireNonNull(entities);
        entitiesById.putAll(entities.stream().collect(toMap(E::id, Function.identity())));
    }

    @Override
    public void remove(E... entities) {
        Objects.requireNonNull(entities);
        for (E entity : entities) {
            entitiesById.remove(entity.id());
        }
    }

    @Override
    public void removeAll(Set<E> entities) {
        entitiesById.values().removeAll(entities);
    }

    @Override
    public List<E> search(QueryPredicate<E> query) {
        return entitiesById.values().stream()
                .filter(query::apply)
                .filter(p -> !AudioPlaylistDirectory.ROOT.equals(p))
                .collect(Collectors.toUnmodifiableList());
    }

    @Override
    public Optional<E> findById(int id) {
        return Optional.ofNullable(entitiesById.get(id));
    }

    @Override
    public Optional<E> findByUniqueId(String uniqueId) {
        Objects.requireNonNull(uniqueId);
        return entitiesById.values().stream()
                .filter(entity -> entity.getUniqueId().equals(uniqueId))
                .filter(p -> !AudioPlaylistDirectory.ROOT.equals(p))
                .findAny();
    }

    @Override
    public <A extends EntityAttribute<V>, V> List<E> findByAttribute(A attribute, V value) {
        return entitiesById.values().stream()
                .filter(entity -> attribute.equalsTo(value).apply(entity))          // Transgressoft Query DSL
//                .filter(entity -> entity.getAttribute(attribute).equals(value))   // Java
                .filter(p -> !AudioPlaylistDirectory.ROOT.equals(p))
                .collect(Collectors.toUnmodifiableList());
    }

    @Override
    public <A extends EntityAttribute<V>, V> Optional<E> findSingleByAttribute(A attribute, V value) throws RepositoryException {
        var results = findByAttribute(attribute, value);
        if (results.isEmpty())
            return Optional.empty();
        else if (results.size() > 1)
            throw new RepositoryException("Found several results when expecting single when searching [" + attribute + ", " + value + "]");
        return Optional.of(results.get(0));
    }

    @Override
    public <A extends EntityAttribute<V>, V, X extends QueryEntity> Optional<X> findSingleByAttribute(A attribute, V value, Class<X> resultType) throws RepositoryException {
        var results = findByAttribute(attribute, value);
        if (results.isEmpty()) {
            return Optional.empty();
        } else if (results.size() > 1) {
            throw new RepositoryException("Found several results when expecting single when searching [" + attribute + ", " + value + "]");
        }
        if (! resultType.isInstance(results.get(0))) {
            throw new RepositoryException("Result " + results.get(0) + " is not of the expected type " + resultType.getSimpleName());
        }
        return Optional.of(resultType.cast(results.get(0)));
    }


    @Override
    public boolean isEmpty() {
        return entitiesById.isEmpty();
    }

    @Override
    public int size() {
        return entitiesById.size();
    }

    @Override
    public UnmodifiableListIterator<E> iterator() {
        return ImmutableList.copyOf(entitiesById.values()).listIterator();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        InMemoryRepository<?> that = (InMemoryRepository<?>) o;
        return Objects.equals(entitiesById, that.entitiesById);
    }

    @Override
    public int hashCode() {
        return Objects.hash(entitiesById);
    }
}
