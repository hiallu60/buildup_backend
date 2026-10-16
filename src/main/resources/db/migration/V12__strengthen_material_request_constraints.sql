-- 자재 요청과 처리 이력에 애플리케이션과 동일한 최소 불변조건을 적용한다.
-- 여러 테이블을 비교해야 하는 기존 데이터 오류는 제약 추가 전에 마이그레이션을 중단한다.
CREATE TEMPORARY TABLE v12_material_migration_guard (
    issue_count BIGINT NOT NULL,
    CONSTRAINT chk_v12_material_migration_ready CHECK (issue_count = 0)
);

INSERT INTO v12_material_migration_guard (issue_count)
SELECT
    (SELECT COUNT(*)
     FROM material_requests request
     WHERE NOT EXISTS (
         SELECT 1
         FROM material_request_items item
         WHERE item.request_id = request.id
     ))
    +
    (SELECT COUNT(*)
     FROM material_request_events event
     WHERE event.actor_user_id IS NULL)
    +
    (SELECT COUNT(*)
     FROM material_requests request
     LEFT JOIN material_request_events latest
            ON latest.request_id = request.id
           AND latest.position = (
               SELECT MAX(event.position)
               FROM material_request_events event
               WHERE event.request_id = request.id
           )
     WHERE latest.request_id IS NULL
        OR latest.status <> request.status);

DROP TEMPORARY TABLE v12_material_migration_guard;

ALTER TABLE material_requests
    ADD CONSTRAINT chk_material_requests_status
        CHECK (status IN ('REQUESTED', 'APPROVED', 'REJECTED', 'ORDERED', 'DELIVERED')),
    ADD CONSTRAINT chk_material_requests_reject_reason
        CHECK (
            (status = 'REJECTED' AND reject_reason IS NOT NULL AND TRIM(reject_reason) <> '')
            OR
            (status <> 'REJECTED' AND reject_reason IS NULL)
        );

ALTER TABLE material_request_items
    ADD CONSTRAINT chk_material_request_items_position
        CHECK (position >= 0);

ALTER TABLE material_request_events
    MODIFY COLUMN actor_user_id BIGINT NOT NULL,
    ADD CONSTRAINT chk_material_request_events_position
        CHECK (position >= 0),
    ADD CONSTRAINT chk_material_request_events_status
        CHECK (status IN ('REQUESTED', 'APPROVED', 'REJECTED', 'ORDERED', 'DELIVERED'));
