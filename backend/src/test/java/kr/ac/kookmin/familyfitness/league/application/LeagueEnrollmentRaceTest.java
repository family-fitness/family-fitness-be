package kr.ac.kookmin.familyfitness.league.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.function.Consumer;
import kr.ac.kookmin.familyfitness.league.application.port.LeagueRepository;
import kr.ac.kookmin.familyfitness.league.domain.LeagueBusyException;
import kr.ac.kookmin.familyfitness.league.domain.LeagueMember;
import kr.ac.kookmin.familyfitness.league.domain.LeagueMove;
import kr.ac.kookmin.familyfitness.league.domain.LeagueRound;
import kr.ac.kookmin.familyfitness.league.domain.LeagueTier;
import kr.ac.kookmin.familyfitness.shared.domain.ErrorKind;
import kr.ac.kookmin.familyfitness.support.ProfileRows;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 방 배정이 다른 요청과 겹칠 때 — test 프로필의 H2 에 실제로 커밋하며 V146 의 유니크 인덱스 · 기본 키를 직접 건드린다.
 *
 * <p>실제 저장소({@link LeagueRepository} 빈)를 감싸 insert 바로 앞에 「다른 요청」 을 끼워 넣는다. 끼운 일은 별도 트랜잭션
 * (REQUIRES_NEW)에서 먼저 커밋되므로, 뒤이은 우리 insert 는 DB 에서 SQLSTATE 23505 를 받는다. 이 시험이 보는 것:
 * 유니크 위반을 false 로 받는가(SqlErrors), 그 트랜잭션을 예외 없이 되돌리는가, 새로 읽어 다시 고르는가, 다섯 번이면 멈추는가.
 * 다른 시험과 섞이지 않게 2001년 1월 방만 쓰고 끝나면 지운다.
 */
@SpringBootTest
@ActiveProfiles("test")
class LeagueEnrollmentRaceTest {
    private static final YearMonth MONTH = YearMonth.of(2001, 1);
    private static final Instant NOW = Instant.parse("2001-01-15T01:00:00Z");

    @Autowired
    LeagueRepository repository;

    @Autowired
    LeagueSettlement settlement;

    @Autowired
    TransactionTemplate tx;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    ProfileRows rows;

    @Autowired
    JdbcTemplate jdbc;

    private final List<UUID> families = new ArrayList<>();
    private final Racing racing = new Racing();

    @AfterEach
    void tearDown() {
        jdbc.update("delete from league_members where round_month = ?", MONTH.atDay(1));
        jdbc.update("delete from league_rounds where round_month = ?", MONTH.atDay(1));
        families.forEach(it -> jdbc.update("delete from families where id = ?", it));
    }

    private UUID family(String name) {
        UUID id = rows.family(name);
        families.add(id);
        return id;
    }

    private LeagueEnrollment enrollment() {
        return new LeagueEnrollment(
                racing, settlement, tx, Clock.fixed(NOW, Clock.systemUTC().getZone()));
    }

    /** 다른 요청 — 우리 트랜잭션을 잠시 내려놓고 새 트랜잭션에서 커밋한다. */
    private void otherRequest(Runnable work) {
        TransactionTemplate requiresNew = new TransactionTemplate(transactionManager);
        requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        requiresNew.executeWithoutResult(status -> work.run());
    }

    /** 브론즈 1번 방을 열고 {@code seated} 가족을 1번부터 앉혀 커밋한다. */
    private LeagueRound committedRoom(UUID... seated) {
        LeagueRound room = LeagueRound.open(MONTH, LeagueTier.BRONZE, 1, NOW);
        otherRequest(() -> {
            repository.insertRound(room);
            for (int i = 0; i < seated.length; i++) {
                repository.insertMember(LeagueMember.join(room, seated[i], i + 1, NOW));
            }
        });
        return room;
    }

    /** 그달 자리의 가족 id, 자리 번호 차례 */
    private List<UUID> seatsOf(UUID roundId) {
        return jdbc.queryForList(
                "select family_id from league_members where round_id = ? order by seat_no", UUID.class, roundId);
    }

    private int rowsOf(UUID familyId) {
        Integer count = jdbc.queryForObject(
                "select count(*) from league_members where family_id = ? and round_month = ?",
                Integer.class,
                familyId,
                MONTH.atDay(1));
        return count == null ? 0 : count;
    }

    @Test
    @DisplayName("고른 자리를 다른 가족이 먼저 차지하면 되돌리고 새로 읽어 다음 자리에 앉는다")
    void 한_번_뺏기면_다음_자리() {
        UUID neighbor = family("이웃");
        UUID rival = family("먼저 온 가족");
        UUID ours = family("서준이네");
        LeagueRound room = committedRoom(neighbor);
        racing.beforeNextMember(
                chosen -> repository.insertMember(LeagueMember.join(room, rival, chosen.seatNo(), NOW)));

        LeagueMember joined = enrollment().ensureMember(ours, MONTH);

        assertThat(joined.roundId()).isEqualTo(room.id());
        assertThat(joined.seatNo()).isEqualTo(3);
        assertThat(racing.memberInserts)
                .as("첫 시도는 유니크 위반(round_id, seat_no)으로 false")
                .isEqualTo(2);
        assertThat(seatsOf(room.id())).containsExactly(neighbor, rival, ours);
    }

    @Test
    @DisplayName("다섯 번 연달아 자리를 뺏기면 더 시도하지 않고 409 LEAGUE_BUSY — 우리 가족 자리는 남지 않는다")
    void 다섯_번_뺏기면_LEAGUE_BUSY() {
        UUID ours = family("서준이네");
        LeagueRound room = committedRoom();
        List<UUID> rivals = new ArrayList<>();
        for (int i = 0; i < LeagueEnrollment.MAX_ATTEMPTS; i++) {
            UUID rival = family("먼저 온 가족" + i);
            rivals.add(rival);
            racing.beforeNextMember(
                    chosen -> repository.insertMember(LeagueMember.join(room, rival, chosen.seatNo(), NOW)));
        }

        assertThatThrownBy(() -> enrollment().ensureMember(ours, MONTH))
                .isInstanceOfSatisfying(LeagueBusyException.class, e -> {
                    assertThat(e.getCode()).isEqualTo("LEAGUE_BUSY");
                    assertThat(e.getKind()).isEqualTo(ErrorKind.CONFLICT);
                });
        assertThat(racing.memberInserts).isEqualTo(LeagueEnrollment.MAX_ATTEMPTS);
        assertThat(rowsOf(ours)).isZero();
        assertThat(seatsOf(room.id())).containsExactlyElementsOf(rivals);
    }

    @Test
    @DisplayName("새 방을 다른 요청이 먼저 열면(같은 달 · 티어 · 방 번호) 되돌리고 그 방의 다음 자리에 앉는다")
    void 새_방을_먼저_열면_그_방에_앉는다() {
        UUID rival = family("먼저 온 가족");
        UUID ours = family("서준이네");
        List<LeagueRound> opened = new ArrayList<>();
        racing.beforeNextRound(chosen -> {
            LeagueRound theirs = LeagueRound.open(chosen.month(), chosen.tier(), chosen.groupNo(), NOW);
            repository.insertRound(theirs);
            repository.insertMember(LeagueMember.join(theirs, rival, 1, NOW));
            opened.add(theirs);
        });

        LeagueMember joined = enrollment().ensureMember(ours, MONTH);

        assertThat(racing.roundInserts)
                .as("첫 시도는 유니크 위반(round_month, tier, group_no)으로 false")
                .isEqualTo(1);
        assertThat(joined.roundId()).isEqualTo(opened.getFirst().id());
        assertThat(joined.seatNo()).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                        "select count(*) from league_rounds where round_month = ?", Integer.class, MONTH.atDay(1)))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("같은 가족을 두 요청이 겹쳐 넣으면 늦은 쪽은 먼저 들어간 자리를 돌려준다 — 행을 덮어쓰지 않는다")
    void 같은_가족_두_요청() {
        UUID ours = family("서준이네");
        LeagueRound room = committedRoom();
        racing.beforeNextMember(
                chosen -> repository.insertMember(LeagueMember.join(room, ours, chosen.seatNo() + 1, NOW)));

        LeagueMember joined = enrollment().ensureMember(ours, MONTH);

        assertThat(racing.memberInserts)
                .as("늦은 쪽 insert 는 기본 키(round_id, family_id) 위반으로 false")
                .isEqualTo(1);
        assertThat(joined.seatNo()).as("먼저 들어간 요청의 자리").isEqualTo(2);
        assertThat(rowsOf(ours)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "select seat_no from league_members where round_id = ? and family_id = ?",
                        Integer.class,
                        room.id(),
                        ours))
                .isEqualTo(2);
    }

    @Test
    @DisplayName("유니크가 아닌 제약 위반(없는 가족 FK)은 false 로 삼키지 않고 그대로 던진다")
    void 다른_제약_위반은_던진다() {
        LeagueRound room = committedRoom();
        LeagueMember stranger = LeagueMember.join(room, UUID.randomUUID(), 1, NOW);

        assertThatThrownBy(() -> tx.executeWithoutResult(status -> repository.insertMember(stranger)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(seatsOf(room.id())).isEmpty();
    }

    /**
     * 실제 저장소를 그대로 부르되, insert 바로 앞에 넣어 둔 「다른 요청」 을 하나씩 먼저 커밋한다.
     * 넣은 차례대로 insert 한 번마다 하나씩 쓴다.
     */
    private final class Racing implements LeagueRepository {
        int roundInserts;
        int memberInserts;
        private final Queue<Consumer<LeagueRound>> beforeRounds = new ArrayDeque<>();
        private final Queue<Consumer<LeagueMember>> beforeMembers = new ArrayDeque<>();

        void beforeNextRound(Consumer<LeagueRound> work) {
            beforeRounds.add(work);
        }

        void beforeNextMember(Consumer<LeagueMember> work) {
            beforeMembers.add(work);
        }

        @Override
        public boolean insertRound(LeagueRound round) {
            roundInserts++;
            Consumer<LeagueRound> work = beforeRounds.poll();
            if (work != null) otherRequest(() -> work.accept(round));
            return repository.insertRound(round);
        }

        @Override
        public boolean insertMember(LeagueMember member) {
            memberInserts++;
            Consumer<LeagueMember> work = beforeMembers.poll();
            if (work != null) otherRequest(() -> work.accept(member));
            return repository.insertMember(member);
        }

        @Override
        public @Nullable LeagueRound findRound(UUID roundId) {
            return repository.findRound(roundId);
        }

        @Override
        public List<LeagueRound> roundsOf(YearMonth month, LeagueTier tier) {
            return repository.roundsOf(month, tier);
        }

        @Override
        public List<LeagueRound> unsettledRoundsOf(YearMonth month) {
            return repository.unsettledRoundsOf(month);
        }

        @Override
        public @Nullable LeagueMember findMember(UUID familyId, YearMonth month) {
            return repository.findMember(familyId, month);
        }

        @Override
        public @Nullable LeagueMember findLatestMemberBefore(UUID familyId, YearMonth month) {
            return repository.findLatestMemberBefore(familyId, month);
        }

        @Override
        public List<LeagueMember> membersOf(UUID roundId) {
            return repository.membersOf(roundId);
        }

        @Override
        public List<LeagueMember> membersOfMonth(YearMonth month) {
            return repository.membersOfMonth(month);
        }

        @Override
        public boolean markSettled(UUID roundId, Instant at) {
            return repository.markSettled(roundId, at);
        }

        @Override
        public void saveResult(
                UUID roundId,
                UUID familyId,
                @Nullable Integer finalRate,
                @Nullable Double finalScore,
                @Nullable Integer finalRank,
                LeagueMove moved) {
            repository.saveResult(roundId, familyId, finalRate, finalScore, finalRank, moved);
        }
    }
}
