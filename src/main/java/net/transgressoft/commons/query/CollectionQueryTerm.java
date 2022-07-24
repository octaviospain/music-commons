package net.transgressoft.commons.query;

import com.google.common.collect.Sets;

import java.util.Set;
import java.util.function.Function;

public interface CollectionQueryTerm<E extends QueryEntity> extends Function<E, Set<E>> {

    default CollectionQueryTerm<E> add(CollectionQueryTerm<E> function) {
        return entity -> Sets.union(this.apply(entity), function.apply(entity));
    }
}
