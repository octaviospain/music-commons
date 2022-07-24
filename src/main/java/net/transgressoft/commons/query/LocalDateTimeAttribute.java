package net.transgressoft.commons.query;

import java.time.LocalDateTime;

public interface LocalDateTimeAttribute<E extends QueryEntity<A>, A extends EntityAttribute<?>, V> extends EntityAttribute<V> {

    default BinaryQueryTermBase<E, A, LocalDateTime, LocalDateTime> isBefore(LocalDateTime localDateTime) {
        return new BinaryQueryTermBase<>((A) this, localDateTime) {
            @Override
            public boolean apply(LocalDateTime attributeValue) {
                return attributeValue.isBefore(localDateTime);
            }
        };
    }

    default BinaryQueryTermBase<E, A, LocalDateTime, LocalDateTime> isAfter(LocalDateTime localDateTime) {
        return new BinaryQueryTermBase<>((A) this, localDateTime) {
            @Override
            public boolean apply(LocalDateTime attributeValue) {
                return attributeValue.isAfter(localDateTime);
            }
        };
    }
}
