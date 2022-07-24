package net.transgressoft.commons.query;

import java.util.function.Function;

public interface BooleanQueryTerm<E extends QueryEntity> extends Function<E, Boolean> {

    default BooleanQueryTerm<E> and(BooleanQueryTerm<E> function) {
        return entity -> this.apply(entity) && function.apply(entity);
    }
}
