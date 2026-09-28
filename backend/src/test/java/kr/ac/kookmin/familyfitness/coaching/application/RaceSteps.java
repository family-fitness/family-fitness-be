package kr.ac.kookmin.familyfitness.coaching.application;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.jspecify.annotations.Nullable;

/** 실제 DB 경합 시험의 공통 도구 — 한 요청을 정한 자리에서 멈춰 세우고, 그 사이 다른 요청을 보낸다. */
final class RaceSteps {
    /** 멈춘 요청 뒤에 보낸 요청이 잠금 앞까지 가도록 잠깐 둔다. H2 잠금 대기 기본값(2초)보다 짧아야 한다. */
    private static final long SETTLE_MILLIS = 300;

    private RaceSteps() {}

    record Race<A, B>(Future<A> first, Future<B> second) {}

    /**
     * {@code first} 를 돌려 {@code pause} 에서 멈추게 한 뒤 {@code second} 를 돌린다. {@code second} 가 잠금 앞까지 가도록 잠깐 두고
     * {@code first} 를 풀어 준다. 둘 다 끝난 뒤 돌려준다. 잠깐 두는 시간은 결과를 가르지 않는다 — {@code second} 가 늦게 닿으면
     * {@code first} 가 먼저 커밋한 뒤라 잠금을 기다린 때와 결과가 같다.
     */
    static <A, B> Race<A, B> race(Pause pause, Callable<A> first, Callable<B> second) throws InterruptedException {
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<A> firstDone = pool.submit(first);
            pause.awaitReached();
            Future<B> secondDone = pool.submit(second);
            try {
                secondDone.get(SETTLE_MILLIS, TimeUnit.MILLISECONDS);
            } catch (ExecutionException | TimeoutException e) {
                // 잠금을 기다리는 중이거나 이미 끝났다. 어느 쪽이든 결과는 아래 호출한 쪽이 Future 로 본다.
            } finally {
                pause.release();
            }
            return new Race<>(firstDone, secondDone);
        }
    }

    static <T> @Nullable T resultOf(Future<T> future) {
        if (future.state() == Future.State.FAILED) throw new AssertionError("성공해야 했다", future.exceptionNow());
        return future.resultNow();
    }

    static Throwable failureOf(Future<?> future) {
        if (future.state() != Future.State.FAILED) throw new AssertionError("실패해야 했다: " + future.state());
        return future.exceptionNow();
    }

    /** 한 요청을 정한 자리에서 멈춰 세운다. 멈춘 동안 그 요청의 트랜잭션 · 잠금은 그대로다. */
    static final class Pause {
        private final CountDownLatch reached = new CountDownLatch(1);
        private final CountDownLatch released = new CountDownLatch(1);

        void hold() {
            reached.countDown();
            await(released, "풀어 주지 않았습니다");
        }

        void awaitReached() {
            await(reached, "멈출 자리까지 오지 않았습니다");
        }

        void release() {
            released.countDown();
        }

        private static void await(CountDownLatch latch, String timeoutMessage) {
            try {
                if (!latch.await(10, TimeUnit.SECONDS)) throw new IllegalStateException(timeoutMessage);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
    }
}
