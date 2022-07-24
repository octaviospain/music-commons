package net.transgressoft.commons.query;

import java.nio.file.Path;
import java.util.Objects;

public interface PathAttribute<E extends QueryEntity<A>, A extends EntityAttribute<?>, V> extends EntityAttribute<V> {

    default BinaryQueryTermBase<E, A, Path, Path> equals(Path path) {
        return new BinaryQueryTermBase<>((A) this, path) {
            @Override
            public boolean apply(Path attributeValue) {
                return Objects.equals(path, attributeValue);
            }
        };
    }

    default BinaryQueryTermBase<E, A, Path, Path> notEquals(Path path) {
        return new BinaryQueryTermBase<>((A) this, path) {
            @Override
            public boolean apply(Path attributeValue) {
                return !Objects.equals(path, attributeValue);
            }
        };

    }
}
