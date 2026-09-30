package kr.ac.kookmin.familyfitness.shared.dev;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.config.ScheduledTaskHolder;

/**
 * `app.dev.time-travel.enabled=true` 이면 서버 시계({@code clock} 빈)를 옮길 수 있는 시계로 바꾼다. 꺼져 있으면(기본 · 운영)
 * {@link kr.ac.kookmin.familyfitness.shared.config.CoreConfig} 의 시스템 시계가 선다. local · compose 가 아닌 프로필에서 켜면
 * {@link kr.ac.kookmin.familyfitness.shared.security.DevFeatureGuard} 가 기동을 멈춘다.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "app.dev.time-travel.enabled", havingValue = "true")
public class DevClockConfig {
    @Bean
    public ShiftableClock clock() {
        return new ShiftableClock(Clock.systemUTC());
    }

    @Bean
    public TimeTravel timeTravel(ShiftableClock clock, ZoneId appZone, ObjectProvider<ScheduledTaskHolder> holders) {
        return new TimeTravel(clock, appZone, holders);
    }
}
