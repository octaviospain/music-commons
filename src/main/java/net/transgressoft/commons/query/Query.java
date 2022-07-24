package net.transgressoft.commons.query;

public interface Query<E extends QueryEntity<A>, A extends EntityAttribute<?>, V> {

    boolean matches(E queryEntity);

    Query<E, A, V> and(QueryTerm<E, A, V> queryTerm);
}
