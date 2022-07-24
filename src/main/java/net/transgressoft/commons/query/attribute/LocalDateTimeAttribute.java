package net.transgressoft.commons.query.attribute;

import net.transgressoft.commons.query.BooleanQueryTerm;
import net.transgressoft.commons.query.QueryEntity;

import java.time.LocalDateTime;

public interface LocalDateTimeAttribute extends EntityAttribute<LocalDateTime> {

    default <E extends QueryEntity> BooleanQueryTerm<E> isBefore(LocalDateTime localDateTime) {
        return queryEntity -> queryEntity.getAttribute(this).isBefore(localDateTime);
    }

    default <E extends QueryEntity> BooleanQueryTerm<E> isAfter(LocalDateTime localDateTime) {
        return queryEntity -> queryEntity.getAttribute(this).isAfter(localDateTime);
    }
}
