package kr.ac.kookmin.familyfitness.shared.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class SqlErrorsTest {
    /** 스프링이 번역한 모양 — SQLException 이 원인 사슬 안쪽에 있다. */
    private static DataIntegrityViolationException wrapped(String sqlState) {
        return new DataIntegrityViolationException(
                "could not execute statement", new RuntimeException(new SQLException("violation", sqlState)));
    }

    @Test
    @DisplayName("외래 키 위반은 PostgreSQL 23503 과 H2 의 부모 없음 23506 둘 다 — 유니크 위반 23505 와 섞이지 않는다")
    void 외래_키_위반은_23503_과_23506() {
        assertThat(SqlErrors.isForeignKeyViolation(wrapped("23503"))).isTrue();
        assertThat(SqlErrors.isForeignKeyViolation(wrapped("23506"))).isTrue();
        assertThat(SqlErrors.isForeignKeyViolation(wrapped("23505"))).isFalse();
        assertThat(SqlErrors.isForeignKeyViolation(wrapped("23502"))).isFalse();
        assertThat(SqlErrors.isForeignKeyViolation(new IllegalStateException("no sql")))
                .isFalse();

        assertThat(SqlErrors.isUniqueViolation(wrapped("23505"))).isTrue();
        assertThat(SqlErrors.isUniqueViolation(wrapped("23503"))).isFalse();
    }
}
