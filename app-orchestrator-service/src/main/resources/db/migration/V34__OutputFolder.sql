alter table job_information add column output_folder text default null;
alter table job_information drop column workspace;
alter table job_information drop column project;
alter table job_information drop column shared_file_system_mounts;
alter table job_information drop column cow;
alter table job_information drop column mount_mode;
