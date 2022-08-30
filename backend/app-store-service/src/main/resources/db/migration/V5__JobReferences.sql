alter table jobs
  drop column app_name;

alter table jobs
  drop column app_version;

alter table jobs
  add column application_name varchar(255);

alter table jobs
  add column application_version varchar(255);

alter table jobs
  add constraint FK_jobs_application
    foreign key (application_name, application_version)
      references applications;
