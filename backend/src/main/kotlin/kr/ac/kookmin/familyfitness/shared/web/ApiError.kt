package kr.ac.kookmin.familyfitness.shared.web

/** 실패 응답의 유일한 형태. `{"error": {"code": "...", "message": "..."}}` */
data class ApiError(
    val error: Body,
) {
    data class Body(
        val code: String,
        val message: String,
    )

    companion object {
        fun of(
            code: String,
            message: String,
        ) = ApiError(Body(code, message))
    }
}
