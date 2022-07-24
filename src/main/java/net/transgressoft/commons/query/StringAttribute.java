package net.transgressoft.commons.query;

public interface StringAttribute<E extends QueryEntity<A>, A extends EntityAttribute<?>, V> extends EntityAttribute<V> {

    default BinaryQueryTerm<E, A, String, String> equals(String string) {
        return new BinaryQueryTermBase<>((A) this, string) {
            @Override
            public boolean apply(String attributeValue) {
                return attributeValue.equals(string);
            }
        };
    }

    default BinaryQueryTerm<E, A, String, String> notEquals(String string) {
        return new BinaryQueryTermBase<>((A) this, string) {
            @Override
            public boolean apply(String attributeValue) {
                return ! attributeValue.equals(string);
            }
        };
    }

    default BinaryQueryTerm<E, A, String, String> contains(String string) {
        return new BinaryQueryTermBase<>((A) this, string) {
            @Override
            public boolean apply(String attributeValue) {
                return attributeValue.contains(string);
            }
        };
    }

    default BinaryQueryTerm<E, A, String, String> notContains(String string) {
        return new BinaryQueryTermBase<>((A) this, string) {
            @Override
            public boolean apply(String attributeValue) {
                return ! attributeValue.contains(string);
            }
        };
    };
}
