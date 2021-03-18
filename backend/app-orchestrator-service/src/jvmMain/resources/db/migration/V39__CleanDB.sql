DROP TABLE application_tags;
DROP TABLE applications_application_tags;
DROP TABLE favorited_by;
DROP TABLE jobs;
ALTER TABLE job_information DROP CONSTRAINT IF EXISTS job_information_application_name_application_version_fkey;
DROP TABLE applications;
DROP TABLE tools;
