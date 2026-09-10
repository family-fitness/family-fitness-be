package kr.ac.kookmin.familyfitness.support

import org.mockito.ArgumentCaptor
import org.mockito.Mockito

/**
 * Kotlin 에서 Mockito 매처를 non-null 파라미터에 넘길 때의 NPE 를 피한다.
 * `Mockito.any()` 는 null 을 돌려주는데 Kotlin 이 non-null 인자 검사로 막아서, 제네릭으로 감싸 검사를 우회한다.
 */
@Suppress("UNCHECKED_CAST")
fun <T> anyArg(): T = Mockito.any<T>() ?: (null as T)

@Suppress("UNCHECKED_CAST")
fun <T> capture(captor: ArgumentCaptor<T>): T = captor.capture() ?: (null as T)
