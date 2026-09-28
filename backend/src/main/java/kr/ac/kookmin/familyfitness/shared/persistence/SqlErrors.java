package kr.ac.kookmin.familyfitness.shared.persistence;

import java.sql.SQLException;
import org.jspecify.annotations.Nullable;

/** DB 오류를 드라이버 메시지가 아니라 SQLSTATE 로 가른다. 메시지는 드라이버 버전 · 서버 언어 설정에 따라 바뀐다. */
public final class SqlErrors {
    /** SQLSTATE 23505 unique_violation. PostgreSQL · H2 모두 이 값을 쓴다. */
    static final String UNIQUE_VIOLATION = "23505";

    private SqlErrors() {}

    /** 원인 사슬 어딘가에 SQLSTATE 23505 가 있으면 유니크 위반이다. JDBC 직접 호출 · JPA flush 어느 쪽에서 와도 같다. */
    public static boolean isUniqueViolation(Throwable e) {
        for (@Nullable Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && UNIQUE_VIOLATION.equals(sql.getSQLState())) return true;
        }
        return false;
    }
}
