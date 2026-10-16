-- 알림 참조값과 기기 토큰에 애플리케이션과 동일한 최소 불변조건을 적용한다.
-- 기존 데이터가 제약을 위반하면 스키마 변경 전에 마이그레이션을 중단한다.
CREATE TEMPORARY TABLE v13_notification_device_guard (
    issue_count BIGINT NOT NULL,
    CONSTRAINT chk_v13_notification_device_ready CHECK (issue_count = 0)
);

INSERT INTO v13_notification_device_guard (issue_count)
SELECT
    (SELECT COUNT(*)
     FROM notifications
     WHERE (reference_type IS NULL) <> (reference_id IS NULL))
    +
    (SELECT COUNT(*)
     FROM notifications
     WHERE read_at IS NOT NULL
       AND read_at < created_at)
    +
    (SELECT COUNT(*)
     FROM device_tokens
     WHERE CHAR_LENGTH(TRIM(token)) = 0);

DROP TEMPORARY TABLE v13_notification_device_guard;

ALTER TABLE notifications
    ADD CONSTRAINT chk_notifications_reference_pair
        CHECK ((reference_type IS NULL) = (reference_id IS NULL)),
    ADD CONSTRAINT chk_notifications_read_time
        CHECK (read_at IS NULL OR read_at >= created_at);

ALTER TABLE device_tokens
    ADD CONSTRAINT chk_device_tokens_not_blank
        CHECK (CHAR_LENGTH(TRIM(token)) > 0);
