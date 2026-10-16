-- 애플리케이션 검증을 우회한 데이터도 최소 범위는 DB가 거부하도록 한다.
ALTER TABLE site_processes
    ADD CONSTRAINT chk_site_processes_weight
        CHECK (weight > 0 AND weight <= 100),
    ADD CONSTRAINT chk_site_processes_progress
        CHECK (progress >= 0 AND progress <= 100),
    ADD CONSTRAINT chk_site_processes_sort_order
        CHECK (sort_order >= 0);

ALTER TABLE reports
    ADD CONSTRAINT chk_reports_from_progress
        CHECK (from_progress >= 0 AND from_progress <= 100),
    ADD CONSTRAINT chk_reports_to_progress
        CHECK (to_progress >= 0 AND to_progress <= 100),
    ADD CONSTRAINT chk_reports_progress_increase
        CHECK (to_progress > from_progress),
    ADD CONSTRAINT chk_reports_workers
        CHECK (workers IS NULL OR workers >= 0);

ALTER TABLE material_request_items
    ADD CONSTRAINT chk_material_request_items_quantity
        CHECK (quantity > 0);

-- report_photos.size_bytes와 report_files.size_bytes의 음수 방지는 V10에서 적용한다.
