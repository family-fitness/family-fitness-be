-- 측정을 등록하면 그 아이(about_profile_id)의 REMEASURE 알림을 지운다(NotificationWriter). 그 조회와 fk_notifications_about 참조 쪽 인덱스.
create index ix_notifications_about on notifications (about_profile_id);
