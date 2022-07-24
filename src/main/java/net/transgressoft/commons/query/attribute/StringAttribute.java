package net.transgressoft.commons.query.attribute;

import net.transgressoft.commons.query.BooleanQueryTerm;
import net.transgressoft.commons.query.QueryEntity;

public interface StringAttribute extends EntityAttribute<String> {

    default <E extends QueryEntity> BooleanQueryTerm<E> contains(String string) {
        return queryEntity -> queryEntity.getAttribute(this).contains(string);
    }

    default <E extends QueryEntity> BooleanQueryTerm<E> notContains(String string) {
        return queryEntity -> ! queryEntity.getAttribute(this).contains(string);
    }
}
