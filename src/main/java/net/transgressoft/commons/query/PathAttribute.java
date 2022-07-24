package net.transgressoft.commons.query;

import java.nio.file.Path;

public interface PathAttribute extends EntityAttribute<Path> {

    default <E extends QueryEntity> QueryFunction<E> contains(String string) {
        return queryEntity ->
                queryEntity.getAttribute(this).toAbsolutePath().toString()
                        .contains(string);
}

    default <E extends QueryEntity> QueryFunction<E> notContains(String string) {
        return queryEntity ->
                ! queryEntity.getAttribute(this).toAbsolutePath().toString()
                        .contains(string);
    }
}
