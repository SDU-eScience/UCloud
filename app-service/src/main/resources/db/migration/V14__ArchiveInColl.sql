set search_path to app;

alter table job_information add column archive_in_collection varchar(1024) not null default '';
