set search_path to app;

alter table job_information
  add column workspace varchar(1024);