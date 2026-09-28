package kr.ac.kookmin.familyfitness.coaching.domain;

import kr.ac.kookmin.familyfitness.shared.domain.DomainException;
import kr.ac.kookmin.familyfitness.shared.domain.ErrorKind;

/** 운동 구간(클립)이 없다. 표에 없거나, 새 판에서 빠져 꺼졌거나, 운동이 아닌 구간(휴식 · 인사)이다. */
public class ClipNotFoundException extends DomainException {
    public ClipNotFoundException(String clipId) {
        super("CLIP_NOT_FOUND", ErrorKind.NOT_FOUND, "운동 구간이 없습니다: " + clipId);
    }
}
