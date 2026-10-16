ALTER TABLE sites
    ADD COLUMN progress_calculation_mode VARCHAR(30) NOT NULL DEFAULT 'PROCESS_WEIGHTED';

ALTER TABLE sites
    ADD CONSTRAINT chk_sites_progress_calculation_mode
        CHECK (progress_calculation_mode IN ('PROCESS_WEIGHTED', 'MANUAL_INPUT'));
