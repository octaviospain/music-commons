package net.transgressoft.commons.query;

import java.util.Objects;

public interface IntegerAttribute<E extends QueryEntity<A>, A extends EntityAttribute<?>, V> extends EntityAttribute<V> {

    default BinaryQueryTermBase<E, A, Integer, Integer> equals(Integer integer) {
        return new BinaryQueryTermBase<>((A) this, integer) {
            @Override
            public boolean apply(Integer attributeValue) {
                return Objects.equals(integer, attributeValue);
            }
        };
    }

    default BinaryQueryTermBase<E, A, Integer, Integer> notEquals(Integer integer) {
        return new BinaryQueryTermBase<>((A) this, integer) {
            @Override
            public boolean apply(Integer attributeValue) {
                return ! Objects.equals(integer, attributeValue);
            }
        };
    }
}
