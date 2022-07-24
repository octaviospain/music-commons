package net.transgressoft.commons.query;

public interface BinaryQueryTerm<E extends QueryEntity<A>, A extends EntityAttribute<?>, V, O> extends QueryTerm<E, A, V> {

    O operand();
}
