package kr.ac.kookmin.familyfitness.shared.web;

/** 실패 응답의 유일한 형태. `{"error": {"code": "...", "message": "..."}}` */
public record ApiError(Body error) {
    public record Body(String code, String message) {}

    public static ApiError of(String code, String message) {
        return new ApiError(new Body(code, message));
    }
}
