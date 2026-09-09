package kr.ac.kookmin.familyfitness.fitness.application

import jakarta.annotation.PostConstruct
import kr.ac.kookmin.familyfitness.fitness.application.port.NormRepository
import kr.ac.kookmin.familyfitness.fitness.domain.NormTable
import kr.ac.kookmin.familyfitness.fitness.domain.PercentileCalculator
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * 부팅 시 `fitness_norms` 를 [NormTable] 로 메모리에 올린다. 규준을 다시 적재하면 [refresh] 로 통째로 바꾼다.
 * 백분위는 저장 시점 값으로 굳으므로 표를 바꿔도 과거 기록은 그대로다.
 */
@Component
class NormCatalog(
    private val normRepository: NormRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Volatile
    private var current: NormTable = NormTable.EMPTY

    @PostConstruct
    fun refresh() {
        val points = normRepository.findAll()
        current = NormTable.of(points)
        log.info("체력 규준 적재: {}행 → {}구간", points.size, current.size)
    }

    fun table(): NormTable = current

    fun calculator(): PercentileCalculator = PercentileCalculator(current)
}
