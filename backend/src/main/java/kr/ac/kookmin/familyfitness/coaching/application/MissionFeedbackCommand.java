package kr.ac.kookmin.familyfitness.coaching.application;

import java.util.UUID;
import kr.ac.kookmin.familyfitness.coaching.domain.MissionFeel;

/** 운동 느낌 보내기. profileId 는 느낌을 남기는 참여자(부모가 계정 없는 아이 이름으로 보낼 수 있다). */
public record MissionFeedbackCommand(UUID profileId, MissionFeel feel) {}
