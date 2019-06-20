set search_path to app;

alter table job_information
    add column shared_file_system_mounts jsonb;
