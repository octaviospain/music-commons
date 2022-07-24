package net.transgressoft.commons.query.attribute;

import net.transgressoft.commons.query.QueryEntity;

public class UnknownAttributeException extends RuntimeException {

    public UnknownAttributeException(EntityAttribute<?> entityAttribute, Class<? extends QueryEntity> queryEntityClass) {
        super(String.format("Unknown attribute %s provided for %s", entityAttribute, queryEntityClass.getName()));
    }
}
