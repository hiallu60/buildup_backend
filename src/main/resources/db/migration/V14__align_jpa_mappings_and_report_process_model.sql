-- 모바일 보고서는 work를 필수 내용으로 사용하고, process_key/process_name은 레거시 공정 연결용 선택 쌍으로 유지한다.
-- 동시 수정 유실을 막기 위해 현장과 현장 구성원에 JPA 낙관적 잠금 버전을 추가한다.

UPDATE reports
SET work = TRIM(process_name)
WHERE (work IS NULL OR TRIM(work) = '')
  AND process_name IS NOT NULL
  AND TRIM(process_name) <> '';

CREATE TEMPORARY TABLE v14_jpa_mapping_guard (
    issue_count BIGINT NOT NULL,
    CONSTRAINT chk_v14_jpa_mapping_ready CHECK (issue_count = 0)
);

INSERT INTO v14_jpa_mapping_guard (issue_count)
SELECT
    (SELECT COUNT(*)
     FROM reports
     WHERE work IS NULL OR TRIM(work) = '')
    +
    (SELECT COUNT(*)
     FROM reports
     WHERE (process_key IS NULL) <> (process_name IS NULL))
    +
    (SELECT COUNT(*)
     FROM material_requests
     WHERE (process_key IS NULL) <> (process_name IS NULL));

DROP TEMPORARY TABLE v14_jpa_mapping_guard;

ALTER TABLE reports
    MODIFY COLUMN work VARCHAR(500) NOT NULL,
    ADD CONSTRAINT chk_reports_work_not_blank
        CHECK (CHAR_LENGTH(TRIM(work)) > 0),
    ADD CONSTRAINT chk_reports_process_pair
        CHECK ((process_key IS NULL) = (process_name IS NULL));

ALTER TABLE material_requests
    ADD CONSTRAINT chk_material_requests_process_pair
        CHECK ((process_key IS NULL) = (process_name IS NULL));

ALTER TABLE sites
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0 AFTER id;

ALTER TABLE site_members
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0 AFTER id;
