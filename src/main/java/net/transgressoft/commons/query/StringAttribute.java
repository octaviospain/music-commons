package net.transgressoft.commons.query;

public interface StringAttribute extends EntityAttribute<String> {

    default <E extends QueryEntity> QueryFunction<E> contains(String string) {
        return queryEntity -> queryEntity.getAttribute(this).contains(string);
    }

    default <E extends QueryEntity> QueryFunction<E> notContains(String string) {
        return queryEntity -> queryEntity.getAttribute(this).contains(string);
    }
}
