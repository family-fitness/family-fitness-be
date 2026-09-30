package kr.ac.kookmin.familyfitness.shared.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * local 시연 모드: `X-Dev-User-Id` 헤더를 보낸 요청만 그 계정으로 인증한다(스크립트 · curl 용). 헤더가 없으면 운영과 같아서 브라우저는
 * 로그인 화면으로 간다. 어느 출처에서 불러도 CORS 가 막지 않는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {"app.auth.dev-auto-login.enabled=true", "app.cors.allowed-origins=*"})
class DevAutoLoginTest {
    @Autowired
    MockMvc mvc;

    @Test
    @DisplayName("헤더도 토큰도 없으면 운영처럼 401 이다 — 브라우저는 로그인 화면으로 간다")
    void 헤더도_토큰도_없으면_운영처럼_401_이다() throws Exception {
        mvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("빈 X-Dev-User-Id 헤더는 없는 것과 같다 — 401")
    void 빈_X_Dev_User_Id_헤더는_없는_것과_같다() throws Exception {
        mvc.perform(get("/api/v1/me").header(DevAutoLoginFilter.DEV_USER_HEADER, " "))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("X-Dev-User-Id 헤더로 데모 부모가 된다")
    void X_Dev_User_Id_헤더로_데모_부모가_된다() throws Exception {
        mvc.perform(get("/api/v1/me")
                        .header(DevAutoLoginFilter.DEV_USER_HEADER, "00000000-0000-4000-8000-000000000001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("00000000-0000-4000-8000-000000000001"))
                .andExpect(jsonPath("$.nextStep").value("HOME"));
    }

    @Test
    @DisplayName("X-Dev-User-Id 헤더로 다른 시드 계정이 된다")
    void X_Dev_User_Id_헤더로_다른_시드_계정이_된다() throws Exception {
        mvc.perform(get("/api/v1/me")
                        .header(DevAutoLoginFilter.DEV_USER_HEADER, "00000000-0000-4000-8000-000000000002"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("00000000-0000-4000-8000-000000000002"))
                .andExpect(jsonPath("$.nextStep").value("CREATE_FAMILY"));
    }

    @Test
    @DisplayName("X-Dev-User-Id 가 UUID 가 아니면 500 이 아니라 400")
    void X_Dev_User_Id_가_UUID_가_아니면_400() throws Exception {
        mvc.perform(get("/api/v1/me").header(DevAutoLoginFilter.DEV_USER_HEADER, "not-a-uuid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("임의 출처의 프리플라이트를 허용한다")
    void 임의_출처의_프리플라이트를_허용한다() throws Exception {
        mvc.perform(options("/api/v1/me")
                        .header("Origin", "http://192.168.0.7:5173")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://192.168.0.7:5173"));
    }
}
