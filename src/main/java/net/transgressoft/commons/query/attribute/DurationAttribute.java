package net.transgressoft.commons.query.attribute;

import net.transgressoft.commons.query.BooleanQueryTerm;
import net.transgressoft.commons.query.QueryEntity;

import java.time.Duration;

public interface DurationAttribute extends EntityAttribute<Duration> {

    default <E extends QueryEntity> BooleanQueryTerm<E> isShorterThan(Duration duration) {
        return queryEntity -> queryEntity.getAttribute(this).compareTo(duration) < 0;
    }

    default <E extends QueryEntity> BooleanQueryTerm<E> isLongerThan(Duration duration) {
        return queryEntity -> queryEntity.getAttribute(this).compareTo(duration) > 0;
    }
}
