alter table job_information drop column mountMode;
alter table job_information add column mount_mode varchar(255) default null;
