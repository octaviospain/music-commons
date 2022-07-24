package net.transgressoft.commons.query;

import java.time.Duration;

public interface DurationAttribute<E extends QueryEntity<A>, A extends EntityAttribute<?>, V> extends EntityAttribute<V> {

    default BinaryQueryTermBase<E, A, Duration, Duration> lessThan(Duration duration) {
        return new BinaryQueryTermBase<>((A) this, duration) {
            @Override
            public boolean apply(Duration attributeValue) {
                return attributeValue.compareTo(duration) < 0;
            }
        };
    }

    default BinaryQueryTermBase<E, A, Duration, Duration> longerThan(Duration duration) {
        return new BinaryQueryTermBase<>((A) this, duration) {
            @Override
            public boolean apply(Duration attributeValue) {
                return attributeValue.compareTo(duration) > 0;
            }
        };
    }
}
