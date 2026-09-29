package kr.ac.kookmin.familyfitness.shared.dev;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import kr.ac.kookmin.familyfitness.shared.domain.DomainException;
import kr.ac.kookmin.familyfitness.shared.domain.ErrorKind;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.IntervalTask;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.scheduling.config.Task;
import org.springframework.scheduling.support.SimpleTriggerContext;

/**
 * 개발용 시간 이동. 서버 시계({@link ShiftableClock})를 앞으로 옮기면서, 그 사이에 돌았어야 할 정시 작업(cron — 07:30 오늘의 미션 알림 ·
 * 09:00 다시 재기 알림 · 04:00 토큰 정리 · 매월 1일 00:10 리그 정산 …)을 원래 시각 차례대로 돌린다. 작업마다 시계를 그 시각에 맞춘 뒤
 * 부르므로, 작업이 읽는 「오늘」 · 「지금」 과 남기는 시각이 실제로 그 시각에 돈 것과 같다. 간격 작업(멈춘 코치 실행 정리)은 도착한 뒤 한 번 돈다.
 *
 * <p>작업은 스프링에 등록된 정시 작업 목록({@link ScheduledTaskHolder})에서 읽는다. 모듈을 직접 참조하지 않아서, 새 정시 작업이 생겨도 여기를
 * 고치지 않는다. 뒤로는 가지 못한다 — 이미 쌓인 기록이 「앞날」 이 되어 규칙이 엉킨다. 처음으로 돌아가려면 서버를 다시 띄운다(H2 인메모리).
 */
public class TimeTravel {
    /** 한 번에 옮길 수 있는 가장 긴 폭. 2주 여정 · 월말 리그를 넉넉히 넘는다. */
    static final Duration FARTHEST = Duration.ofDays(400);

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final ShiftableClock clock;
    private final ZoneId zone;
    private final ObjectProvider<ScheduledTaskHolder> holders;

    public TimeTravel(ShiftableClock clock, ZoneId zone, ObjectProvider<ScheduledTaskHolder> holders) {
        this.clock = clock;
        this.zone = zone;
        this.holders = holders;
    }

    public ClockView now() {
        Instant now = clock.instant();
        return new ClockView(now, LocalDate.ofInstant(now, zone), clock.offset());
    }

    /** {@code by} 만큼 앞으로. */
    public Trip travelBy(Duration by) {
        return travelTo(clock.instant().plus(by));
    }

    /** {@code target} 까지 앞으로 옮기며 건너뛴 정시 작업을 돌린다. 한 번에 한 이동만 한다. */
    public synchronized Trip travelTo(Instant target) {
        Instant from = clock.instant();
        if (target.isBefore(from)) {
            throw new DomainException(
                    "TIME_TRAVEL_BACKWARD", ErrorKind.CONFLICT, "서버 시계는 앞으로만 옮긴다: 지금 " + from + ", 요청 " + target);
        }
        if (Duration.between(from, target).compareTo(FARTHEST) > 0) {
            throw new DomainException(
                    "TIME_TRAVEL_TOO_FAR", ErrorKind.BAD_REQUEST, "한 번에 " + FARTHEST.toDays() + "일보다 멀리 옮기지 않는다");
        }

        PriorityQueue<Due> queue =
                new PriorityQueue<>(Comparator.comparing(Due::at).thenComparing(due -> name(due.task())));
        List<IntervalTask> intervals = new ArrayList<>();
        for (Task task : tasks()) {
            if (task instanceof CronTask cron) {
                Instant next = nextAfter(cron, from);
                if (next != null) queue.add(new Due(next, cron));
            } else if (task instanceof IntervalTask interval) {
                intervals.add(interval);
            }
        }

        Map<String, Ran> ran = new LinkedHashMap<>();
        while (!queue.isEmpty() && !queue.peek().at().isAfter(target)) {
            Due due = queue.poll();
            clock.setInstant(due.at());
            run(due.task(), due.at(), ran);
            Instant next = nextAfter(due.task(), due.at());
            if (next != null) queue.add(new Due(next, due.task()));
        }
        clock.setInstant(target);
        for (IntervalTask interval : intervals) run(interval, target, ran);

        Instant now = clock.instant();
        log.info("서버 시계를 옮겼다: {} → {} (정시 작업 {})", from, now, ran.values());
        return new Trip(from, now, LocalDate.ofInstant(now, zone), clock.offset(), List.copyOf(ran.values()));
    }

    private Set<Task> tasks() {
        Set<Task> out = new LinkedHashSet<>();
        holders.orderedStream()
                .flatMap(holder -> holder.getScheduledTasks().stream())
                .map(ScheduledTask::getTask)
                .forEach(out::add);
        return out;
    }

    private static @Nullable Instant nextAfter(CronTask task, Instant after) {
        return task.getTrigger().nextExecution(new SimpleTriggerContext(after, after, after));
    }

    private void run(Task task, Instant at, Map<String, Ran> ran) {
        String name = name(task);
        boolean ok = true;
        try {
            task.getRunnable().run();
        } catch (RuntimeException e) {
            ok = false;
            log.error("시간 이동 중 정시 작업이 실패했다: {} @ {}", name, at, e);
        }
        Ran before = ran.get(name);
        ran.put(name, before == null ? Ran.first(name, at, ok) : before.again(at, ok));
    }

    /**
     * 「클래스.메서드」 — 패키지는 뗀다. 중첩 클래스는 {@code Outer.Inner}. {@code SchedulingConfigurer} 로 등록한 람다는 메서드 이름을
     * 알 수 없어 클래스 이름만 쓴다.
     */
    static String name(Task task) {
        String full = task.getRunnable().toString();
        int lambda = full.indexOf("$$Lambda");
        if (lambda >= 0) {
            String type = full.substring(0, lambda);
            return type.substring(type.lastIndexOf('.') + 1);
        }
        int method = full.lastIndexOf('.');
        if (method < 0) return full;
        String type = full.substring(0, method);
        String simple = type.substring(type.lastIndexOf('.') + 1).replace('$', '.');
        return simple + full.substring(method);
    }

    private record Due(Instant at, CronTask task) {}

    /** 지금 서버 시각. {@code offset} 은 실제 시각에서 옮긴 폭. */
    public record ClockView(Instant now, LocalDate today, Duration offset) {}

    /** 한 번 옮긴 결과. {@code ran} 은 작업마다 한 줄, 처음 돈 차례대로. */
    public record Trip(Instant from, Instant now, LocalDate today, Duration offset, List<Ran> ran) {}

    /**
     * 작업 하나를 이번 이동에서 돌린 결과. {@code first} · {@code last} 는 그 작업이 원래 돌았어야 할 시각(간격 작업은 도착 시각),
     * {@code failures} 는 그중 실패한 횟수(로그에 원인이 남는다).
     */
    public record Ran(String task, int runs, int failures, Instant first, Instant last) {
        static Ran first(String task, Instant at, boolean ok) {
            return new Ran(task, 1, ok ? 0 : 1, at, at);
        }

        Ran again(Instant at, boolean ok) {
            return new Ran(task, runs + 1, failures + (ok ? 0 : 1), first, at);
        }
    }
}
