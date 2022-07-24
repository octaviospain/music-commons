package net.transgressoft.commons.query;

import java.util.Objects;

public interface EntityAttribute<V> {

    default <E extends QueryEntity> QueryFunction<E> equalsTo(V object) {
        return queryEntity -> Objects.equals(queryEntity.getAttribute(this), object);
    }

    default <E extends QueryEntity> QueryFunction<E> notEqualsTo(V object) {
        return queryEntity -> ! Objects.equals(queryEntity.getAttribute(this), object);
    }
}
