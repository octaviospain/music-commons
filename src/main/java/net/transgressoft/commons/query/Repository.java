package net.transgressoft.commons.query;

import com.google.common.collect.UnmodifiableListIterator;
import net.transgressoft.commons.music.playlist.RepositoryException;
import net.transgressoft.commons.query.attribute.EntityAttribute;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface Repository<E extends QueryEntity> extends Iterable<E> {

    void add(E... entity) throws RepositoryException;

    void addAll(Set<E> entities) throws RepositoryException;

    void remove(E... entity);

    void removeAll(Set<E> entities);

    List<E> search(QueryPredicate<E> query);

    Optional<E> findById(int id);

    Optional<E> findByUniqueId(String uniqueId);

    <A extends EntityAttribute<V>, V> List<E> findByAttribute(A attribute, V value);

    <A extends EntityAttribute<V>, V> Optional<E> findSingleByAttribute(A attribute, V value) throws RepositoryException;

    <A extends EntityAttribute<V>, V, X extends QueryEntity> Optional<X> findSingleByAttribute(A attribute, V value, Class<X> resultType) throws RepositoryException;

    int size();

    boolean isEmpty();

    UnmodifiableListIterator<E> iterator();
}
