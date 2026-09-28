package kr.ac.kookmin.familyfitness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * V151 이 이미 거둬진 보호자(PARENT) 동의를 푸는지 본다(QA KP-01). V149 까지 적용 → 다른 보호자가 거둔 상태의 행을 넣음 → V151 적용.
 * 빈 DB 에 전체를 적용하는 시험으로는 UPDATE 가 행 0개로 돈다. H2 는 늘 돌고, PostgreSQL 은 Docker 가 있을 때만(CI) 돈다.
 */
class ParentConsentVoidMigrationTests {
    private static final OffsetDateTime AT = OffsetDateTime.of(2026, 9, 20, 9, 0, 0, 0, ZoneOffset.ofHours(9));
    private static final OffsetDateTime REVOKED_AT = AT.plusDays(1);
    private static final UUID FAMILY = UUID.randomUUID();
    private static final UUID MOM_USER = UUID.randomUUID();
    private static final UUID DAD_USER = UUID.randomUUID();
    private static final UUID MOM = UUID.randomUUID();
    private static final UUID DAD = UUID.randomUUID();
    private static final UUID KID = UUID.randomUUID();

    @Test
    @DisplayName("H2: 거둔 채인 보호자 동의만 풀고, 거둔 줄은 두고 무효로 한 줄(VOIDED)을 더한다 — 아이의 철회는 그대로")
    void H2_에서_보호자_철회를_푼다() throws SQLException {
        String url = "jdbc:h2:mem:v151-" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1";
        verifyVoid(url, "sa", "");
    }

    @Test
    @DisplayName("PostgreSQL: 같은 정리가 실제 PostgreSQL 에서도 돈다")
    void PostgreSQL_에서_보호자_철회를_푼다() throws SQLException {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker 가 없어 건너뛴다");
        try (PostgreSQLContainer pg = new PostgreSQLContainer(DockerImageName.parse("postgres:latest"))) {
            pg.start();
            verifyVoid(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword());
        }
    }

    /** 앱처럼 연결 풀 하나로 Flyway 와 JDBC 를 함께 쓴다(CheerKindBackfillMigrationTests 의 H2 CHECK 제약 주석 참고). */
    private static void verifyVoid(String url, String user, String password) throws SQLException {
        try (HikariDataSource pool = new HikariDataSource()) {
            pool.setJdbcUrl(url);
            pool.setUsername(user);
            pool.setPassword(password);
            migrate(pool, "149");
            try (Connection c = pool.getConnection()) {
                insertUser(c, MOM_USER);
                insertUser(c, DAD_USER);
                insertFamily(c);
                // 아빠가 엄마(PARENT)의 동의를 거뒀다 — 앞으로는 막히는 요청이다
                insertProfile(c, MOM, MOM_USER, "PARENT", true, LocalDate.of(1985, 4, 1), REVOKED_AT);
                insertProfile(c, DAD, DAD_USER, "PARENT", false, LocalDate.of(1984, 2, 1), null);
                // 아이의 철회는 제대로 된 철회라 그대로 둔다
                insertProfile(c, KID, null, "CHILD", false, LocalDate.of(2016, 5, 1), REVOKED_AT);
                insertRevoked(c, MOM);
                insertRevoked(c, KID);
            }

            migrate(pool, "151");

            try (Connection c = pool.getConnection()) {
                assertThat(revokedAt(c, MOM)).isNull();
                assertThat(revokedAt(c, DAD)).isNull();
                assertThat(revokedAt(c, KID)).isNotNull();
                assertThat(version(c, MOM)).isOne();
                assertThat(version(c, KID)).isZero();
                assertThat(events(c, MOM)).containsExactly("REVOKED/" + DAD_USER, "VOIDED/-");
                assertThat(events(c, KID)).containsExactly("REVOKED/" + DAD_USER);
                assertThat(events(c, DAD)).isEmpty();
            }
        }
    }

    private static void migrate(DataSource dataSource, String target) {
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target(target)
                .load()
                .migrate();
    }

    private static void insertUser(Connection c, UUID id) throws SQLException {
        try (PreparedStatement user = c.prepareStatement("insert into users (id, provider, provider_user_id, status,"
                + " created_at, updated_at) values (?, 'DEV', ?, 'ACTIVE', ?, ?)")) {
            user.setObject(1, id);
            user.setString(2, "v151-" + id);
            user.setObject(3, AT);
            user.setObject(4, AT);
            user.executeUpdate();
        }
    }

    private static void insertFamily(Connection c) throws SQLException {
        try (PreparedStatement family = c.prepareStatement(
                "insert into families (id, name, created_at, updated_at) values (?, '시험가족', ?, ?)")) {
            family.setObject(1, FAMILY);
            family.setObject(2, AT);
            family.setObject(3, AT);
            family.executeUpdate();
        }
    }

    private static void insertProfile(
            Connection c,
            UUID id,
            @Nullable UUID userId,
            String role,
            boolean owner,
            LocalDate birthDate,
            @Nullable OffsetDateTime revokedAt)
            throws SQLException {
        try (PreparedStatement profile = c.prepareStatement("insert into profiles (id, family_id, user_id,"
                + " display_name, birth_date, sex, role, is_owner, consent_revoked_at, created_at, updated_at)"
                + " values (?, ?, ?, ?, ?, 'F', ?, ?, ?, ?, ?)")) {
            profile.setObject(1, id);
            profile.setObject(2, FAMILY);
            profile.setObject(3, userId);
            profile.setString(4, role + "-" + id.toString().substring(0, 4));
            profile.setObject(5, birthDate);
            profile.setString(6, role);
            profile.setBoolean(7, owner);
            profile.setObject(8, revokedAt);
            profile.setObject(9, AT);
            profile.setObject(10, AT);
            profile.executeUpdate();
        }
    }

    private static void insertRevoked(Connection c, UUID profileId) throws SQLException {
        try (PreparedStatement event = c.prepareStatement("insert into consent_events (profile_id, actor_user_id,"
                + " kind, personal_data, health_data, occurred_at) values (?, ?, 'REVOKED', false, false, ?)")) {
            event.setObject(1, profileId);
            event.setObject(2, DAD_USER);
            event.setObject(3, REVOKED_AT);
            event.executeUpdate();
        }
    }

    private static @Nullable OffsetDateTime revokedAt(Connection c, UUID profileId) throws SQLException {
        try (PreparedStatement query = c.prepareStatement("select consent_revoked_at from profiles where id = ?")) {
            query.setObject(1, profileId);
            try (ResultSet rs = query.executeQuery()) {
                rs.next();
                return rs.getObject(1, OffsetDateTime.class);
            }
        }
    }

    private static long version(Connection c, UUID profileId) throws SQLException {
        try (PreparedStatement query = c.prepareStatement("select version from profiles where id = ?")) {
            query.setObject(1, profileId);
            try (ResultSet rs = query.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    /** 그 사람의 동의 이력, 일어난 차례로. 「kind/누가」 이고 누가가 없으면 - */
    private static List<String> events(Connection c, UUID profileId) throws SQLException {
        List<String> events = new ArrayList<>();
        try (PreparedStatement query = c.prepareStatement(
                "select kind, actor_user_id from consent_events where profile_id = ? order by occurred_at, id")) {
            query.setObject(1, profileId);
            try (ResultSet rs = query.executeQuery()) {
                while (rs.next()) {
                    UUID actor = rs.getObject(2, UUID.class);
                    events.add(rs.getString(1) + "/" + (actor == null ? "-" : actor));
                }
            }
        }
        return events;
    }
}
