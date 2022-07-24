package net.transgressoft.commons.query;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toMap;

public class InMemoryRepository<E extends QueryEntity> implements Repository<E> {

    private final Map<Integer, E> entitiesById;

    public InMemoryRepository() {
        this(Collections.emptyList());
    }

    public InMemoryRepository(Collection<E> entities) {
        Objects.requireNonNull(entities);
        entitiesById = entities.stream().collect(toMap(E::id, Function.identity()));
    }

    @Override
    public synchronized void add(E entity) {
        Objects.requireNonNull(entity);
        entitiesById.put(entity.id(), entity);
    }

    @Override
    public synchronized void addAll(List<E> entities) {
        Objects.requireNonNull(entities);
        entitiesById.putAll(entities.stream().collect(toMap(E::id, Function.identity())));
    }

    @Override
    public synchronized void remove(E entity) {
        Objects.requireNonNull(entity);
        entitiesById.remove(entity.id());
    }

    @Override
    public synchronized void removeAll(Set<E> entities) {
        entitiesById.values().removeAll(entities);
    }

    @Override
    public synchronized List<E> search(QueryFunction<E> query) {
        return entitiesById.values().stream()
                .filter(query::apply)
                .collect(Collectors.toUnmodifiableList());
    }

    @Override
    public synchronized Optional<E> findById(int id) {
        return Optional.ofNullable(entitiesById.get(id));
    }

    @Override
    public synchronized Optional<E> findByUniqueId(String uniqueId) {
        Objects.requireNonNull(uniqueId);
        return entitiesById.values().stream()
                .filter(entity -> entity.uniqueId().equals(uniqueId))
                .findAny();
    }

    @Override
    public <A extends EntityAttribute<V>, V> List<E> findByAttribute(A attribute, V value) {
        return entitiesById.values().stream()
                .filter(entity -> attribute.equalsTo(value).apply(entity))          // Transgressoft Query DSL
//                .filter(entity -> entity.getAttribute(attribute).equals(value))   // Java
                .collect(Collectors.toUnmodifiableList());
    }

    @Override
    public boolean isEmpty() {
        return entitiesById.isEmpty();
    }

    @Override
    public int size() {
        return entitiesById.size();
    }

    @Override //TODO can be improved ensuring immutability of the collection overriding the Iterator methods
    public synchronized Iterator<E> iterator() {
        return entitiesById.values().iterator();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        InMemoryRepository<?> that = (InMemoryRepository<?>) o;
        return Objects.equals(entitiesById, that.entitiesById);
    }

    @Override
    public int hashCode() {
        return Objects.hash(entitiesById);
    }
}
