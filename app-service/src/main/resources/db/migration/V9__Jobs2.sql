set search_path to app;

create table job_information
(
  system_id           varchar(255)  not null,

  owner               varchar(255)  not null,
  access_token        varchar(4096) not null,

  application_name    varchar(255)  not null,
  application_version varchar(255)  not null,

  backend_name        varchar(255)  not null,

  files               jsonb,
  parameters          jsonb,
  nodes               integer       not null,
  tasks_per_node      integer       not null,

  max_time_hours      integer       not null,
  max_time_minutes    integer       not null,
  max_time_seconds    integer       not null,

  state               varchar(255)  not null,
  status              varchar(255)  not null,


  created_at          timestamp     not null,
  modified_at         timestamp     not null,
  primary key (system_id),
  foreign key (application_name, application_version) references applications
);
