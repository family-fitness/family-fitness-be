package kr.ac.kookmin.familyfitness.shared.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling
import java.time.Clock
import java.time.ZoneId

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AppProperties::class)
@EnableAsync
@EnableScheduling
class CoreConfig {
    /** 모든 "지금"은 이 Clock 을 통해 얻는다. 테스트에서 고정 시각으로 바꾼다. */
    @Bean
    fun clock(): Clock = Clock.systemUTC()

    /** 활동 날짜(activityDate) 등 사용자 기준 날짜를 계산하는 시간대. */
    @Bean
    fun appZone(props: AppProperties): ZoneId = ZoneId.of(props.timezone)
}
