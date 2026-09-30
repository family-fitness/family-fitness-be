package kr.ac.kookmin.familyfitness.shared.dev;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.support.TestAuth;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** 시간 이동은 켜야만 있다. 기본(운영 포함)은 시스템 시계이고 주소도 없다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TimeTravelOffTest {
    private static final UUID PARENT = UUID.fromString("00000000-0000-4000-8000-000000000001");

    @Autowired
    MockMvc mvc;

    @Autowired
    Clock clock;

    @Autowired
    TestAuth auth;

    @Test
    @DisplayName("꺼져 있으면 서버 시계는 옮길 수 없는 시스템 시계다")
    void 꺼져_있으면_서버_시계는_옮길_수_없는_시스템_시계다() {
        assertThat(clock).isNotInstanceOf(ShiftableClock.class);
    }

    @Test
    @DisplayName("꺼져 있으면 /dev/clock 주소가 없다")
    void 꺼져_있으면_dev_clock_주소가_없다() throws Exception {
        mvc.perform(get("/api/v1/dev/clock").header(HttpHeaders.AUTHORIZATION, auth.bearer(PARENT)))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/dev/clock")
                        .header(HttpHeaders.AUTHORIZATION, auth.bearer(PARENT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"by\":\"P1D\"}"))
                .andExpect(status().isNotFound());
    }
}
