package net.transgressoft.commons.query.attribute;

import net.transgressoft.commons.query.BooleanQueryTerm;
import net.transgressoft.commons.query.QueryEntity;

public interface NumberAttribute<V extends Number> extends EntityAttribute<V> {

    default <E extends QueryEntity> BooleanQueryTerm<E> isLessThan(V number) {
        return queryEntity -> queryEntity.getAttribute(this).doubleValue() < number.doubleValue();
    }

    default <E extends QueryEntity> BooleanQueryTerm<E> isGreaterThan(V number) {
        return queryEntity -> queryEntity.getAttribute(this).doubleValue() > number.doubleValue();
    }
}
