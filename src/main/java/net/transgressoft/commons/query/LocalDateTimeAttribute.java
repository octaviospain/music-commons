package net.transgressoft.commons.query;

import java.time.LocalDateTime;

public interface LocalDateTimeAttribute extends EntityAttribute<LocalDateTime> {

    default <E extends QueryEntity> QueryFunction<E> isBefore(LocalDateTime localDateTime) {
        return queryEntity -> queryEntity.getAttribute(this).isBefore(localDateTime);
    }

    default <E extends QueryEntity> QueryFunction<E> isAfter(LocalDateTime localDateTime) {
        return queryEntity -> queryEntity.getAttribute(this).isAfter(localDateTime);
    }
}
