-- V10 적용 전 운영 DB에서 직접 실행해 삭제 대상과 이전 상태를 확인한다.

SELECT COUNT(*) AS legacy_auth_token_count
FROM legacy_auth_tokens;

SELECT
    COUNT(*) AS users_with_legacy_company_text,
    SUM(hq_company_id IS NOT NULL) AS users_with_hq_company,
    SUM(manager_company_id IS NOT NULL) AS users_with_manager_company
FROM users
WHERE company IS NOT NULL
   OR hq_company_id IS NOT NULL
   OR manager_company_id IS NOT NULL;

SELECT u.id, u.email, u.company, u.hq_company_id, u.manager_company_id
FROM users u
WHERE (u.hq_company_id IS NOT NULL AND NOT EXISTS (
        SELECT 1
        FROM company_memberships cm
        WHERE cm.user_id = u.id
          AND cm.company_id = u.hq_company_id
    ))
   OR (u.manager_company_id IS NOT NULL AND NOT EXISTS (
        SELECT 1
        FROM company_memberships cm
        WHERE cm.user_id = u.id
          AND cm.company_id = u.manager_company_id
          AND cm.role = 'HQ_MANAGER'
    ));

-- 결과가 있으면 V10은 회사명을 임의의 회사 ID로 바꿀 수 없으므로 중단된다.
SELECT id, email, company
FROM users
WHERE company IS NOT NULL
  AND TRIM(company) <> '';

SELECT COUNT(*) AS photos_without_original_name
FROM report_photos
WHERE original_name IS NULL
  AND file_name IS NULL;

SELECT
    SUM(s3_key IS NOT NULL) AS s3_photo_count,
    SUM(s3_key IS NULL) AS legacy_photo_metadata_count
FROM report_photos;

SELECT
    SUM(s3_key IS NOT NULL) AS s3_file_count,
    SUM(path IS NOT NULL) AS legacy_path_count,
    SUM(s3_key IS NULL AND path IS NULL) AS files_without_storage_reference
FROM report_files;

SELECT COUNT(*) AS existing_photo_checks_without_photo_reference
FROM report_checks
WHERE check_type IN ('OLD_PHOTO', 'FAR_PHOTO');
