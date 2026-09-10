package kr.ac.kookmin.familyfitness.shared.security

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/** local 시연 모드: 토큰 없이 시드 데모 부모로 동작하고, 어느 출처에서 불러도 CORS 가 막지 않는다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(
    properties = [
        "app.auth.dev-auto-login.enabled=true",
        "app.auth.dev-auto-login.user-id=00000000-0000-4000-8000-000000000001",
        "app.cors.allowed-origins=*",
    ],
)
class DevAutoLoginTest {
    @Autowired lateinit var mvc: MockMvc

    @Test
    fun `토큰 없이 me 를 부르면 데모 부모다`() {
        mvc
            .perform(get("/api/v1/me"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.userId").value("00000000-0000-4000-8000-000000000001"))
            .andExpect(jsonPath("$.nextStep").value("HOME"))
    }

    @Test
    fun `X-Dev-User-Id 헤더로 다른 시드 계정이 된다`() {
        mvc
            .perform(get("/api/v1/me").header(DevAutoLoginFilter.DEV_USER_HEADER, "00000000-0000-4000-8000-000000000002"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.userId").value("00000000-0000-4000-8000-000000000002"))
            .andExpect(jsonPath("$.nextStep").value("CREATE_FAMILY"))
    }

    @Test
    fun `임의 출처의 프리플라이트를 허용한다`() {
        mvc
            .perform(
                options("/api/v1/me")
                    .header("Origin", "http://192.168.0.7:5173")
                    .header("Access-Control-Request-Method", "GET")
                    .header("Access-Control-Request-Headers", "authorization,content-type"),
            ).andExpect(status().isOk)
            .andExpect(header().string("Access-Control-Allow-Origin", "http://192.168.0.7:5173"))
    }
}
