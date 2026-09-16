package kr.ac.kookmin.familyfitness.coaching.domain;

import kr.ac.kookmin.familyfitness.shared.domain.DomainException;
import kr.ac.kookmin.familyfitness.shared.domain.ErrorKind;

public class VideoNotFoundException extends DomainException {
    public VideoNotFoundException(String videoId) {
        super("VIDEO_NOT_FOUND", ErrorKind.NOT_FOUND, "영상이 없습니다: " + videoId);
    }
}
