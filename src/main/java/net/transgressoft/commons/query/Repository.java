package net.transgressoft.commons.query;

import java.util.Optional;

public interface Repository<E extends QueryEntity<A>, A extends EntityAttribute<?>> {

    Query<E, A, ?> query();

    Optional<E> findById(int id);

    Optional<E> findByUniqueId(String uniqueId);
}
