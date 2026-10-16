-- 회사 소속 정보가 모두 정규화된 멤버십에 남도록 한 번 더 보정한 뒤 레거시 컬럼을 제거한다.
INSERT INTO company_memberships (company_id, user_id, role, status)
SELECT hq_company_id, id, 'HQ_MEMBER', 'ACTIVE'
FROM users
WHERE hq_company_id IS NOT NULL
ON DUPLICATE KEY UPDATE status = 'ACTIVE';

INSERT INTO company_memberships (company_id, user_id, role, status)
SELECT manager_company_id, id, 'HQ_MANAGER', 'ACTIVE'
FROM users
WHERE manager_company_id IS NOT NULL
ON DUPLICATE KEY UPDATE role = 'HQ_MANAGER', status = 'ACTIVE';

-- 레거시 회사 문자열은 회사 ID로 안전하게 자동 변환할 수 없다.
-- 또한 FK 기반 회사 소속이 멤버십으로 실제 이전되지 않았다면 삭제를 중단한다.
CREATE TEMPORARY TABLE v10_company_migration_guard (
    issue_count BIGINT NOT NULL,
    CONSTRAINT chk_v10_company_migration_complete CHECK (issue_count = 0)
);

INSERT INTO v10_company_migration_guard (issue_count)
SELECT
    (SELECT COUNT(*)
     FROM users u
     WHERE u.company IS NOT NULL
       AND TRIM(u.company) <> '')
    +
    (SELECT COUNT(*)
     FROM users u
     WHERE u.hq_company_id IS NOT NULL
       AND NOT EXISTS (
           SELECT 1
           FROM company_memberships cm
           WHERE cm.user_id = u.id
             AND cm.company_id = u.hq_company_id
       ))
    +
    (SELECT COUNT(*)
     FROM users u
     WHERE u.manager_company_id IS NOT NULL
       AND NOT EXISTS (
           SELECT 1
           FROM company_memberships cm
           WHERE cm.user_id = u.id
             AND cm.company_id = u.manager_company_id
             AND cm.role = 'HQ_MANAGER'
       ));

DROP TEMPORARY TABLE v10_company_migration_guard;

DROP TABLE IF EXISTS legacy_auth_tokens;

ALTER TABLE users
    DROP FOREIGN KEY fk_users_hq_company,
    DROP FOREIGN KEY fk_users_manager_company,
    DROP COLUMN company,
    DROP COLUMN hq_company_id,
    DROP COLUMN manager_company_id;

-- 기존 사진명은 V9에서 original_name으로 복사되었으므로 호환 컬럼을 제거한다.
UPDATE report_photos
SET original_name = file_name
WHERE original_name IS NULL;

ALTER TABLE report_photos
    MODIFY COLUMN original_name VARCHAR(255) NOT NULL,
    DROP COLUMN file_name,
    ADD CONSTRAINT chk_report_photos_size
        CHECK (size_bytes IS NULL OR size_bytes >= 0),
    ADD CONSTRAINT chk_report_photos_location_pair
        CHECK ((latitude IS NULL) = (longitude IS NULL)),
    ADD CONSTRAINT chk_report_photos_latitude
        CHECK (latitude IS NULL OR latitude BETWEEN -90 AND 90),
    ADD CONSTRAINT chk_report_photos_longitude
        CHECK (longitude IS NULL OR longitude BETWEEN -180 AND 180);

-- 신규 첨부파일은 S3 키를 사용하고, 기존 path 데이터는 읽기 호환용으로만 남긴다.
ALTER TABLE report_files
    MODIFY COLUMN path VARCHAR(500) NULL,
    ADD CONSTRAINT chk_report_files_storage_reference
        CHECK (path IS NOT NULL OR s3_key IS NOT NULL),
    ADD CONSTRAINT chk_report_files_size
        CHECK (size_bytes >= 0);

ALTER TABLE sites
    ADD CONSTRAINT chk_sites_location_pair
        CHECK ((latitude IS NULL) = (longitude IS NULL)),
    ADD CONSTRAINT chk_sites_latitude
        CHECK (latitude IS NULL OR latitude BETWEEN -90 AND 90),
    ADD CONSTRAINT chk_sites_longitude
        CHECK (longitude IS NULL OR longitude BETWEEN -180 AND 180);

ALTER TABLE reports
    ADD CONSTRAINT chk_reports_submit_location_pair
        CHECK ((submit_latitude IS NULL) = (submit_longitude IS NULL)),
    ADD CONSTRAINT chk_reports_submit_latitude
        CHECK (submit_latitude IS NULL OR submit_latitude BETWEEN -90 AND 90),
    ADD CONSTRAINT chk_reports_submit_longitude
        CHECK (submit_longitude IS NULL OR submit_longitude BETWEEN -180 AND 180),
    ADD CONSTRAINT chk_reports_submit_accuracy
        CHECK (submit_accuracy IS NULL OR submit_accuracy >= 0);

-- 사진 관련 검사 결과는 원본 사진을 식별하고, 사진 삭제 시 함께 제거한다.
ALTER TABLE report_checks
    ADD COLUMN photo_id BIGINT NULL AFTER report_id,
    ADD CONSTRAINT fk_report_checks_photo
        FOREIGN KEY (photo_id) REFERENCES report_photos (id) ON DELETE CASCADE;

CREATE INDEX idx_report_checks_photo
    ON report_checks (photo_id);
