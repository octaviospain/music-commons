package net.transgressoft.commons.query.attribute;

import net.transgressoft.commons.query.QueryEntity;
import net.transgressoft.commons.query.BooleanQueryTerm;

import java.nio.file.Path;

public interface PathAttribute extends EntityAttribute<Path> {

    default <E extends QueryEntity> BooleanQueryTerm<E> contains(String string) {
        return queryEntity ->
                queryEntity.getAttribute(this).toAbsolutePath().toString()
                        .contains(string);
}

    default <E extends QueryEntity> BooleanQueryTerm<E> notContains(String string) {
        return queryEntity ->
                ! queryEntity.getAttribute(this).toAbsolutePath().toString()
                        .contains(string);
    }
}
