package net.transgressoft.commons.query.attribute;

import net.transgressoft.commons.query.QueryEntity;
import net.transgressoft.commons.query.BooleanQueryTerm;

import java.util.Objects;

public interface EntityAttribute<V> {

    default <E extends QueryEntity> BooleanQueryTerm<E> equalsTo(V object) {
        return queryEntity -> Objects.equals(queryEntity.getAttribute(this), object);
    }

    default <E extends QueryEntity> BooleanQueryTerm<E> notEqualsTo(V object) {
        return queryEntity -> ! Objects.equals(queryEntity.getAttribute(this), object);
    }
}
