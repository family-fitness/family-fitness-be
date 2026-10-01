package kr.ac.kookmin.familyfitness.support;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 지운 id 를 아직 가리키는 행을 DB 전체에서 찾는다. 표와 칸 목록은 information_schema 에서 그때그때 읽는다. 그래서 나중에 표나 칸이
 * 늘어도 이 검사가 빠뜨리지 않고, 새 표의 행을 지우는 일을 잊으면 탈퇴 시험이 깨진다. H2(PostgreSQL 모드)와 PostgreSQL 둘 다에서
 * 돈다.
 *
 * <ul>
 *   <li>{@link #referencing}: uuid 칸 전부(profile_id, family_id, user_id, created_by, approved_by, claim_code_issued_by,
 *       actor_user_id, requested_by_profile_id, mission_id, cheer_id, 기본 키 등). 외래 키가 없는 칸도 본다
 *   <li>{@link #mentioning}: 문자열 컬럼 전부(participants_json, source_key, dedupe_key, lock_key 등). id 가 글자로 들어간 곳을 본다
 * </ul>
 * 결과는 「표.칸」 마다 찾은 행 수다. 아무것도 없으면 빈 Map 이다.
 */
@Component
public class Leftovers {
    private static final String SCHEMA = "public";
    private static final String MIGRATION_HISTORY = "flyway_schema_history";

    private final JdbcTemplate jdbc;

    public Leftovers(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 표 하나의 칸 하나. */
    public record Column(String table, String column, String dataType) {
        boolean isUuid() {
            return dataType.equalsIgnoreCase("uuid");
        }

        boolean isText() {
            String type = dataType.toLowerCase(Locale.ROOT);
            return type.contains("char")
                    || type.equals("text")
                    || type.contains("clob")
                    || type.contains("large object");
        }

        @Override
        public String toString() {
            return table + "." + column;
        }
    }

    /** 이 DB 의 모든 표(뷰와 마이그레이션 기록 표는 뺀다)의 모든 칸. */
    public List<Column> columns() {
        return jdbc
                .query(
                        """
                select c.table_name, c.column_name, c.data_type
                from information_schema.columns c
                join information_schema.tables t
                  on t.table_schema = c.table_schema and t.table_name = c.table_name
                where lower(c.table_schema) = ? and t.table_type = 'BASE TABLE'
                order by c.table_name, c.ordinal_position
                """,
                        (rs, row) -> new Column(
                                rs.getString(1).toLowerCase(Locale.ROOT),
                                rs.getString(2).toLowerCase(Locale.ROOT),
                                rs.getString(3)),
                        SCHEMA)
                .stream()
                .filter(it -> !it.table().equals(MIGRATION_HISTORY))
                .toList();
    }

    /** uuid 칸에 이 id 가운데 하나가 든 행 수. */
    public Map<String, Long> referencing(Collection<UUID> ids) {
        if (ids.isEmpty()) return Map.of();
        List<UUID> values = List.copyOf(ids);
        String marks = String.join(", ", Collections.nCopies(values.size(), "?"));
        Map<String, Long> found = new TreeMap<>();
        for (Column column : columns()) {
            if (!column.isUuid()) continue;
            Long count = jdbc.queryForObject(
                    "select count(*) from " + column.table() + " where " + column.column() + " in (" + marks + ")",
                    Long.class,
                    values.toArray());
            if (count != null && count > 0) found.put(column.toString(), count);
        }
        return found;
    }

    /** 문자열 컬럼에 이 id 가운데 하나가 글자로 든 행 수. */
    public Map<String, Long> mentioning(Collection<UUID> ids) {
        if (ids.isEmpty()) return Map.of();
        List<String> patterns = new ArrayList<>();
        for (UUID id : ids) patterns.add("%" + id + "%");
        Map<String, Long> found = new TreeMap<>();
        for (Column column : columns()) {
            if (!column.isText()) continue;
            String where = String.join(
                    " or ", Collections.nCopies(patterns.size(), "cast(" + column.column() + " as varchar) like ?"));
            Long count = jdbc.queryForObject(
                    "select count(*) from " + column.table() + " where " + where, Long.class, patterns.toArray());
            if (count != null && count > 0) found.put(column.toString(), count);
        }
        return found;
    }
}
