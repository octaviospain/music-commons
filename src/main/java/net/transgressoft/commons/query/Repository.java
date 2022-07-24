package net.transgressoft.commons.query;

import com.google.common.collect.UnmodifiableListIterator;
import net.transgressoft.commons.query.attribute.EntityAttribute;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface Repository<E extends QueryEntity> extends Iterable<E> {

    void add(E entity);

    void addAll(List<E> entities);

    void remove(E entity);

    void removeAll(Set<E> entities);

    List<E> search(QueryPredicate<E> query);

    Optional<E> findById(int id);

    Optional<E> findByUniqueId(String uniqueId);

    <A extends EntityAttribute<V>, V> List<E> findByAttribute(A attribute, V value);

    int size();

    boolean isEmpty();

    UnmodifiableListIterator<E> iterator();
}
