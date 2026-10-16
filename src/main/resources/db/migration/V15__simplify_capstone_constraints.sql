-- 캡스톤 범위에서는 명확한 데이터 무결성 제약만 DB에 남기고,
-- 변경 가능한 업무 규칙과 낮은 확률의 동시 수정 방어는 서비스 로직으로 관리한다.

ALTER TABLE reports
    DROP CHECK chk_reports_progress_increase;

ALTER TABLE material_requests
    DROP CHECK chk_material_requests_reject_reason;

ALTER TABLE notifications
    DROP CHECK chk_notifications_read_time;

ALTER TABLE device_tokens
    DROP CHECK chk_device_tokens_not_blank;

ALTER TABLE sites
    DROP COLUMN version;

ALTER TABLE site_members
    DROP COLUMN version;
