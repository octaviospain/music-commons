package net.transgressoft.commons.query;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.Month;

import static com.google.common.truth.Truth.assertThat;
import static java.time.Duration.ofSeconds;
import static net.transgressoft.commons.query.PersonDurationAttribute.BREATH_DURATION;
import static net.transgressoft.commons.query.PersonFloatAttribute.HEIGHT;
import static net.transgressoft.commons.query.PersonLocalDateTimeAttribute.BIRTH_DATE;
import static net.transgressoft.commons.query.PersonPathAttribute.USER_HOME;
import static net.transgressoft.commons.query.PersonShortAttribute.INTERESTS;
import static net.transgressoft.commons.query.PersonStringAttribute.NAME;


class QueryFunctionTest {

    int id = 23;
    String name = "Obi Wan Kenobi";
    float height = 1.75f;
    short numberOfInterests = 3;
    int money = 50_000;
    LocalDateTime birthDate = LocalDateTime.of(1992, Month.JANUARY, 24, 2, 30);
    Path userHome = Path.of("home", "Obi");
    Duration breathDuration = ofSeconds(80);

    PersonEntity entity = new PersonEntity(id, name, height, numberOfInterests, money, birthDate, userHome, breathDuration);

    @Test
    @DisplayName("One operand queries")
    void simpleQueryTest() {
        assertThat(HEIGHT.isGreaterThan(1.70f).apply(entity)).isTrue();
        assertThat(HEIGHT.isLessThan(1.80f).apply(entity)).isTrue();
        assertThat(BIRTH_DATE.isAfter(LocalDateTime.of(1985, Month.JANUARY, 1, 1, 1, 1)).apply(entity)).isTrue();
        assertThat(BIRTH_DATE.isBefore(LocalDateTime.of(2020, Month.JANUARY, 1, 1, 1, 1)).apply(entity)).isTrue();
        assertThat(USER_HOME.notContains("path").apply(entity)).isTrue();
        assertThat(USER_HOME.contains("Obi").apply(entity)).isTrue();
    }

    @Test
    @DisplayName("AND queries")
    void twoTermsQuery() {
        var query =
                HEIGHT.isGreaterThan(1.70f)
                        .and(NAME.notContains("Leia"));

        assertThat(query.apply(entity)).isTrue();

        query = BIRTH_DATE.isAfter(
                        LocalDateTime.of(1985, Month.JANUARY, 1, 1, 1, 1))
                .and(NAME.contains("Keno"));

        assertThat(query.apply(entity)).isTrue();

        query = INTERESTS.isGreaterThan((short) 1)
                .and(PersonIntegerAttribute.MONEY.isLessThan(1_000_000));

        assertThat(query.apply(entity)).isTrue();

        query = BREATH_DURATION.isLongerThan(ofSeconds(60))
                .and(entity -> entity.id() == 23);

        assertThat(query.apply(entity)).isTrue();

        query = BREATH_DURATION.notEqualsTo(ofSeconds(500))
                .and(entity -> entity.uniqueId().equals("23-Obi Wan Kenobi-1.75"));

        assertThat(query.apply(entity)).isTrue();
    }
}
