ALTER TABLE jobs DROP CONSTRAINT IF EXISTS FK_jobs_application;
ALTER TABLE job_information DROP CONSTRAINT IF EXISTS job_information_application_name_fkey
