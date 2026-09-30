package kr.ac.kookmin.familyfitness.shared.persistence;

import java.sql.SQLException;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** DB 오류를 드라이버 메시지가 아니라 SQLSTATE 로 가른다. 메시지는 드라이버 버전 · 서버 언어 설정에 따라 바뀐다. */
public final class SqlErrors {
    /** SQLSTATE 23505 unique_violation. PostgreSQL · H2 모두 이 값을 쓴다. */
    static final String UNIQUE_VIOLATION = "23505";

    /**
     * 외래 키 위반. PostgreSQL 은 23503 foreign_key_violation 하나를 쓴다. H2 는 자식 행을 넣는데 부모 행이 없으면 23506,
     * 자식 행이 남은 부모 행을 지우면 23503 이다.
     */
    static final Set<String> FOREIGN_KEY_VIOLATION = Set.of("23503", "23506");

    private SqlErrors() {}

    /** 원인 사슬 어딘가에 SQLSTATE 23505 가 있으면 유니크 위반이다. JDBC 직접 호출 · JPA flush 어느 쪽에서 와도 같다. */
    public static boolean isUniqueViolation(Throwable e) {
        return hasSqlState(e, Set.of(UNIQUE_VIOLATION));
    }

    /** 원인 사슬 어딘가에 외래 키 위반 SQLSTATE({@link #FOREIGN_KEY_VIOLATION})가 있으면 true. */
    public static boolean isForeignKeyViolation(Throwable e) {
        return hasSqlState(e, FOREIGN_KEY_VIOLATION);
    }

    private static boolean hasSqlState(Throwable e, Set<String> states) {
        for (@Nullable Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && states.contains(sql.getSQLState())) return true;
        }
        return false;
    }
}
