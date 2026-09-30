package kr.ac.kookmin.familyfitness.shared.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AppProperties.class)
@EnableAsync
@EnableScheduling
public class CoreConfig {
    /**
     * 모든 "지금"은 이 Clock 에서 얻는다. 테스트에서 고정 시각으로 바꾼다. 개발용 시간 이동을 켜면(local · compose)
     * {@link kr.ac.kookmin.familyfitness.shared.dev.DevClockConfig} 의 옮길 수 있는 시계가 대신 선다.
     */
    @Bean
    @ConditionalOnProperty(name = "app.dev.time-travel.enabled", havingValue = "false", matchIfMissing = true)
    public Clock clock() {
        return Clock.systemUTC();
    }

    /** 활동 날짜(activityDate) 등 사용자 기준 날짜를 계산하는 시간대. */
    @Bean
    public ZoneId appZone(AppProperties props) {
        return ZoneId.of(props.timezone());
    }
}
