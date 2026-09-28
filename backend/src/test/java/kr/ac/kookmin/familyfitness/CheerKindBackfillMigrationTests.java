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
import java.util.HashMap;
import java.util.Map;
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
 * V136 이 이미 쌓인 응원 행을 옮기는지 본다. V134 까지 적용 → 옛 모양(emoji) 응원을 넣음 → V136 적용 순서다.
 * 빈 DB 에 전체를 적용하는 시험(FamilyfitnessApplicationTests · PostgresMigrationTests)으로는 백필 UPDATE 가 행 0개로 돈다.
 * H2 는 늘 돌고, PostgreSQL 은 Docker 가 있을 때만(CI) 돈다.
 */
class CheerKindBackfillMigrationTests {
    private static final OffsetDateTime AT = OffsetDateTime.of(2026, 9, 1, 9, 0, 0, 0, ZoneOffset.ofHours(9));
    private static final UUID FAMILY = UUID.randomUUID();
    private static final UUID MOM = UUID.randomUUID();
    private static final UUID DAD = UUID.randomUUID();
    private static final UUID FIRST = UUID.randomUUID();
    private static final UUID SECOND = UUID.randomUUID();

    @Test
    @DisplayName("H2: 지난 응원의 kind 를 역할 · 스티커로 채우고 emoji 값을 sticker_id 로 옮긴다")
    void H2_에서_지난_응원을_옮긴다() throws SQLException {
        String url = "jdbc:h2:mem:v136-" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1";
        verifyBackfill(url, "sa", "");
    }

    @Test
    @DisplayName("PostgreSQL: 같은 백필이 실제 PostgreSQL 에서도 돈다")
    void PostgreSQL_에서_지난_응원을_옮긴다() throws SQLException {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker 가 없어 건너뛴다");
        try (PostgreSQLContainer pg = new PostgreSQLContainer(DockerImageName.parse("postgres:latest"))) {
            pg.start();
            verifyBackfill(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword());
        }
    }

    /**
     * 경우마다 한 행씩 넣고, V136 뒤 (kind, sticker_id) 가 기대와 같은지 본다.
     * 앱처럼 연결 풀 하나로 Flyway 와 JDBC 를 함께 쓴다. H2 는 IN 목록 CHECK 제약(ck_profiles_sex 등)이 그 제약을 만든
     * 세션을 비교에 쓰므로(ConditionInConstantSet), Flyway 연결이 닫히면 그 뒤 insert 가 CHECK_CONSTRAINT_INVALID 로 실패한다.
     */
    private static void verifyBackfill(String url, String user, String password) throws SQLException {
        try (HikariDataSource pool = new HikariDataSource()) {
            pool.setJdbcUrl(url);
            pool.setUsername(user);
            pool.setPassword(password);
            migrate(pool, "134");
            Map<UUID, String> expected = new HashMap<>();
            try (Connection c = pool.getConnection()) {
                insertFamily(c);
                expected.put(insertOldCheer(c, MOM, FIRST, "star", null), "PRAISE/star");
                expected.put(insertOldCheer(c, MOM, FIRST, null, "잘했어"), "PRAISE/-");
                expected.put(insertOldCheer(c, MOM, DAD, null, "수고했어"), "PRAISE/-");
                expected.put(insertOldCheer(c, FIRST, SECOND, "💪", null), "PRAISE/💪");
                expected.put(insertOldCheer(c, FIRST, MOM, "heart", null), "THANKS/heart");
                expected.put(insertOldCheer(c, FIRST, MOM, "clap", "고마워요"), "THANKS/clap");
                expected.put(insertOldCheer(c, FIRST, MOM, null, "다 했어요"), "DONE/-");
            }

            migrate(pool, "136");

            try (Connection c = pool.getConnection()) {
                assertThat(kindAndSticker(c)).isEqualTo(expected);
                assertThat(kindNullable(c)).isEqualTo("NO");
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

    private static void insertFamily(Connection c) throws SQLException {
        try (PreparedStatement family = c.prepareStatement(
                "insert into families (id, name, created_at, updated_at) values (?, '시험가족', ?, ?)")) {
            family.setObject(1, FAMILY);
            family.setObject(2, AT);
            family.setObject(3, AT);
            family.executeUpdate();
        }
        insertProfile(c, MOM, "PARENT", true);
        insertProfile(c, DAD, "PARENT", false);
        insertProfile(c, FIRST, "CHILD", false);
        insertProfile(c, SECOND, "CHILD", false);
    }

    private static void insertProfile(Connection c, UUID id, String role, boolean owner) throws SQLException {
        try (PreparedStatement profile = c.prepareStatement("insert into profiles (id, family_id, display_name,"
                + " birth_date, sex, role, is_owner, created_at, updated_at) values (?, ?, ?, ?, 'F', ?, ?, ?, ?)")) {
            profile.setObject(1, id);
            profile.setObject(2, FAMILY);
            profile.setString(3, role + "-" + id.toString().substring(0, 4));
            profile.setObject(4, LocalDate.of(2015, 1, 1));
            profile.setString(5, role);
            profile.setBoolean(6, owner);
            profile.setObject(7, AT);
            profile.setObject(8, AT);
            profile.executeUpdate();
        }
    }

    /** V134 까지의 모양(emoji 칸, kind 없음)으로 응원 한 행을 넣는다. */
    private static UUID insertOldCheer(
            Connection c, UUID from, UUID to, @Nullable String emoji, @Nullable String message) throws SQLException {
        UUID id = UUID.randomUUID();
        try (PreparedStatement cheer = c.prepareStatement("insert into cheers (id, family_id, from_profile_id,"
                + " to_profile_id, emoji, message, created_at) values (?, ?, ?, ?, ?, ?, ?)")) {
            cheer.setObject(1, id);
            cheer.setObject(2, FAMILY);
            cheer.setObject(3, from);
            cheer.setObject(4, to);
            cheer.setString(5, emoji);
            cheer.setString(6, message);
            cheer.setObject(7, AT);
            cheer.executeUpdate();
        }
        return id;
    }

    private static Map<UUID, String> kindAndSticker(Connection c) throws SQLException {
        Map<UUID, String> actual = new HashMap<>();
        try (PreparedStatement query = c.prepareStatement("select id, kind, sticker_id from cheers");
                ResultSet rs = query.executeQuery()) {
            while (rs.next()) {
                String sticker = rs.getString(3);
                actual.put(rs.getObject(1, UUID.class), rs.getString(2) + "/" + (sticker == null ? "-" : sticker));
            }
        }
        return actual;
    }

    private static String kindNullable(Connection c) throws SQLException {
        try (PreparedStatement query = c.prepareStatement("select is_nullable from information_schema.columns"
                        + " where table_name = 'cheers' and column_name = 'kind'");
                ResultSet rs = query.executeQuery()) {
            rs.next();
            return rs.getString(1);
        }
    }
}
