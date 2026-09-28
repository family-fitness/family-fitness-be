package kr.ac.kookmin.familyfitness.notification.application;

import java.util.List;

/**
 * GET /notifications 응답 — fe:src/lib/api/types.ts NotificationList 그대로.
 *
 * @param items 최신 30건, 만든 시각이 늦은 것부터
 * @param unread 안 읽은 수. 돌려준 것 가운데서 센다(목과 같다). 화면은 숫자를 쓰지 않고 점 하나만 찍는다
 */
public record NotificationListView(List<NotificationView> items, int unread) {}
