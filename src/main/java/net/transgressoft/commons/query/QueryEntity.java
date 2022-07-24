package net.transgressoft.commons.query;

public interface QueryEntity<A extends EntityAttribute<?>> {

    int id();

    String uniqueId();

    AttributeSet<A> attributes();
}
