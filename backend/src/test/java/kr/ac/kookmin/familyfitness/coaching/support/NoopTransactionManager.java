package kr.ac.kookmin.familyfitness.coaching.support;

import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/** 자원 없는 트랜잭션 관리자. 애플리케이션 테스트에서 {@link TransactionTemplate} 을 그대로 쓰기 위한 것. */
public class NoopTransactionManager extends AbstractPlatformTransactionManager {
    @Override
    protected Object doGetTransaction() {
        return new Object();
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {}

    @Override
    protected void doCommit(DefaultTransactionStatus status) {}

    @Override
    protected void doRollback(DefaultTransactionStatus status) {}

    public static TransactionTemplate noopTransactionTemplate() {
        return new TransactionTemplate(new NoopTransactionManager());
    }
}
