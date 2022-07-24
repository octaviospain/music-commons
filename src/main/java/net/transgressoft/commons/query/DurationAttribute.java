package net.transgressoft.commons.query;

import java.time.Duration;

public interface DurationAttribute extends EntityAttribute<Duration> {

    default <E extends QueryEntity> QueryFunction<E>  isShorterThan(Duration duration) {
        return queryEntity -> queryEntity.getAttribute(this).compareTo(duration) < 0;
    }

    default <E extends QueryEntity> QueryFunction<E>  isLongerThan(Duration duration) {
        return queryEntity -> queryEntity.getAttribute(this).compareTo(duration) > 0;
    }
}
