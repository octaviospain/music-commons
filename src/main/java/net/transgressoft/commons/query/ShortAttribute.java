package net.transgressoft.commons.query;

import java.util.Objects;

public interface ShortAttribute<E extends QueryEntity<A>, A extends EntityAttribute<?>, V> extends EntityAttribute<V> {

    default BinaryQueryTermBase<E, A, Short, Short> equals(Short shortValue) {
        return new BinaryQueryTermBase<>((A) this, shortValue) {
            @Override
            public boolean apply(Short attributeValue) {
                return Objects.equals(shortValue, attributeValue);
            }
        };
    }

    default BinaryQueryTermBase<E, A, Short, Short> notEquals(Short shortValue) {
        return new BinaryQueryTermBase<>((A) this, shortValue) {
            @Override
            public boolean apply(Short attributeValue) {
                return ! Objects.equals(shortValue, attributeValue);
            }
        };
    }
}
