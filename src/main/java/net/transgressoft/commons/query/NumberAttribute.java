package net.transgressoft.commons.query;

public interface NumberAttribute<V extends Number> extends EntityAttribute<V> {

    default <E extends QueryEntity> QueryFunction<E> isLessThan(V number) {
        return queryEntity -> queryEntity.getAttribute(this).doubleValue() < number.doubleValue();
    }

    default <E extends QueryEntity> QueryFunction<E> isGreaterThan(V number) {
        return queryEntity -> queryEntity.getAttribute(this).doubleValue() > number.doubleValue();
    }
}
