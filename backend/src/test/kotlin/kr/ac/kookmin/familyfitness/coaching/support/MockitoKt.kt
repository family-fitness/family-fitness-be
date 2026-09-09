package kr.ac.kookmin.familyfitness.coaching.support

import org.mockito.ArgumentMatchers

/**
 * Kotlin 비null 파라미터에 Mockito 매처를 넘기기 위한 도우미.
 * `ArgumentMatchers.any()` 는 null 을 돌려주는데 플랫폼 타입이라 호출 지점에서 null 검사에 걸린다.
 * 제네릭 T 로 돌려주면 검사가 생략되어 Mockito 의 매처 등록만 남는다(mockito-kotlin 과 같은 기법).
 */
object MockitoKt {
    @Suppress("UNCHECKED_CAST")
    fun <T> any(): T {
        ArgumentMatchers.any<T>()
        return null as T
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> eq(value: T): T {
        ArgumentMatchers.eq(value)
        return value
    }
}
