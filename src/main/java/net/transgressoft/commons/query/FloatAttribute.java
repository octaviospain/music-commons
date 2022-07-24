package net.transgressoft.commons.query;

import java.util.Objects;

public interface FloatAttribute<E extends QueryEntity<A>, A extends EntityAttribute<?>, V> extends EntityAttribute<V> {

    default BinaryQueryTermBase<E, A, Float, Float> equals(Float floatValue) {
        return new BinaryQueryTermBase<>((A) this, floatValue) {
            @Override
            public boolean apply(Float attributeValue) {
                return Objects.equals(floatValue, attributeValue);
            }
        };
    }

    default BinaryQueryTermBase<E, A, Float, Float> notEquals(Float floatValue) {
        return new BinaryQueryTermBase<>((A) this, floatValue) {
            @Override
            public boolean apply(Float attributeValue) {
                return ! Objects.equals(floatValue, attributeValue);
            }
        };
    }
}
