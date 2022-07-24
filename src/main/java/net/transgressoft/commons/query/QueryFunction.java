package net.transgressoft.commons.query;

import java.util.function.Function;

public interface QueryFunction<E extends QueryEntity> extends Function<E, Boolean> {

    default QueryFunction<E> and(QueryFunction<E> function) {
        return entity -> this.apply(entity) && function.apply(entity);
    }
}
