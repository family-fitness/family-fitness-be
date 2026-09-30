package kr.ac.kookmin.familyfitness.league.application;

import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.function.Consumer;
import kr.ac.kookmin.familyfitness.league.application.port.LeagueRepository;
import kr.ac.kookmin.familyfitness.league.domain.LeagueMember;
import kr.ac.kookmin.familyfitness.league.domain.LeagueMove;
import kr.ac.kookmin.familyfitness.league.domain.LeagueRound;
import kr.ac.kookmin.familyfitness.league.domain.LeagueTier;
import org.jspecify.annotations.Nullable;

/**
 * league_rounds · league_members 의 메모리 판. 유니크 인덱스 셋(V146)을 같은 조건으로 막는다.
 * {@link #beforeNextMemberInsert} 에 넣은 일은 넣은 차례대로 자리 insert 한 번마다 하나씩, 그 insert 직전에 돈다 —
 * 다른 요청이 먼저 앉은 경우를 만든다. 롤백은 흉내 내지 않으므로 끼운 일 앞에 우리가 쓴 것이 없게 짠다.
 */
class InMemoryLeagueRepository implements LeagueRepository {
    final List<LeagueRound> rounds = new ArrayList<>();
    final List<LeagueMember> members = new ArrayList<>();
    int markSettledCalls = 0;
    int memberInserts = 0;
    private final Queue<Consumer<LeagueMember>> beforeMemberInserts = new ArrayDeque<>();

    void beforeNextMemberInsert(Consumer<LeagueMember> work) {
        beforeMemberInserts.add(work);
    }

    @Override
    public @Nullable LeagueRound findRound(UUID roundId) {
        return rounds.stream().filter(it -> it.id().equals(roundId)).findFirst().orElse(null);
    }

    @Override
    public List<LeagueRound> roundsOf(YearMonth month, LeagueTier tier) {
        return rounds.stream()
                .filter(it -> it.month().equals(month) && it.tier() == tier)
                .sorted(Comparator.comparingInt(LeagueRound::groupNo))
                .toList();
    }

    @Override
    public List<LeagueRound> unsettledRoundsOf(YearMonth month) {
        return rounds.stream()
                .filter(it -> it.month().equals(month) && !it.isSettled())
                .sorted(Comparator.comparing(LeagueRound::tier).thenComparingInt(LeagueRound::groupNo))
                .toList();
    }

    @Override
    public @Nullable LeagueMember findMember(UUID familyId, YearMonth month) {
        return members.stream()
                .filter(it -> it.familyId().equals(familyId) && it.month().equals(month))
                .findFirst()
                .orElse(null);
    }

    @Override
    public @Nullable LeagueMember findLatestMemberBefore(UUID familyId, YearMonth month) {
        return members.stream()
                .filter(it -> it.familyId().equals(familyId) && it.month().isBefore(month))
                .max(Comparator.comparing(LeagueMember::month))
                .orElse(null);
    }

    @Override
    public List<LeagueMember> membersOf(UUID roundId) {
        return members.stream()
                .filter(it -> it.roundId().equals(roundId))
                .sorted(Comparator.comparingInt(LeagueMember::seatNo))
                .toList();
    }

    @Override
    public List<LeagueMember> membersOfMonth(YearMonth month) {
        return members.stream()
                .filter(it -> it.month().equals(month))
                .sorted(Comparator.comparing(LeagueMember::joinedAt).thenComparing(LeagueMember::familyId))
                .toList();
    }

    @Override
    public boolean insertRound(LeagueRound round) {
        boolean taken = rounds.stream()
                .anyMatch(it -> it.month().equals(round.month())
                        && it.tier() == round.tier()
                        && it.groupNo() == round.groupNo());
        if (taken) return false;
        rounds.add(round);
        return true;
    }

    @Override
    public boolean insertMember(LeagueMember member) {
        memberInserts++;
        Consumer<LeagueMember> work = beforeMemberInserts.poll();
        if (work != null) work.accept(member);
        boolean taken = members.stream()
                .anyMatch(it ->
                        (it.familyId().equals(member.familyId()) && it.month().equals(member.month()))
                                || (it.roundId().equals(member.roundId()) && it.seatNo() == member.seatNo()));
        if (taken) return false;
        members.add(member);
        return true;
    }

    @Override
    public boolean markSettled(UUID roundId, Instant at) {
        markSettledCalls++;
        LeagueRound round = findRound(roundId);
        if (round == null || round.isSettled()) return false;
        rounds.set(
                rounds.indexOf(round),
                new LeagueRound(round.id(), round.month(), round.tier(), round.groupNo(), round.createdAt(), at));
        return true;
    }

    @Override
    public void saveResult(
            UUID roundId,
            UUID familyId,
            @Nullable Integer finalRate,
            @Nullable Double finalScore,
            @Nullable Integer finalRank,
            LeagueMove moved) {
        LeagueMember member = members.stream()
                .filter(it -> it.roundId().equals(roundId) && it.familyId().equals(familyId))
                .findFirst()
                .orElseThrow();
        members.set(
                members.indexOf(member),
                new LeagueMember(
                        roundId,
                        familyId,
                        member.month(),
                        member.seatNo(),
                        member.joinedAt(),
                        finalRate,
                        finalScore,
                        finalRank,
                        moved));
    }
}
