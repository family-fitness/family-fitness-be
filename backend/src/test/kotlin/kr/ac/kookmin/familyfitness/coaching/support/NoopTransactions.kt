package kr.ac.kookmin.familyfitness.coaching.support

import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.AbstractPlatformTransactionManager
import org.springframework.transaction.support.DefaultTransactionStatus
import org.springframework.transaction.support.TransactionTemplate

/** 자원 없는 트랜잭션 관리자. 애플리케이션 테스트에서 [TransactionTemplate] 을 그대로 쓰기 위한 것. */
class NoopTransactionManager : AbstractPlatformTransactionManager() {
    override fun doGetTransaction(): Any = Any()

    override fun doBegin(
        transaction: Any,
        definition: TransactionDefinition,
    ) {
    }

    override fun doCommit(status: DefaultTransactionStatus) {
    }

    override fun doRollback(status: DefaultTransactionStatus) {
    }
}

fun noopTransactionTemplate() = TransactionTemplate(NoopTransactionManager())
