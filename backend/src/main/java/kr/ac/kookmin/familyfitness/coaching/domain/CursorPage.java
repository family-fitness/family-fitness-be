package kr.ac.kookmin.familyfitness.coaching.domain;

import java.util.List;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/** 커서 페이지. 정렬된 후보 목록에서 `size` 개를 자르고 다음 커서(마지막 id)를 낸다. */
public record CursorPage<T>(List<T> items, @Nullable String nextCursor) {
    public static <T> CursorPage<T> of(List<T> sorted, int size, Function<T, String> idOf) {
        if (size <= 0) throw new IllegalArgumentException("size 는 1 이상이어야 한다");
        List<T> page = sorted.stream().limit(size).toList();
        String next = sorted.size() > size ? idOf.apply(page.getLast()) : null;
        return new CursorPage<>(page, next);
    }
}
