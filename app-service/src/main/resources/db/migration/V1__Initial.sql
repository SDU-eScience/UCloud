set search_path to app;

create table jobs (
  system_id         uuid not null,
  app_name          varchar(255) not null,
  app_version       varchar(255) not null,
  created_at        timestamp not null,
  modified_at       timestamp not null,
  owner             varchar(255) not null,

  job_directory     varchar(255),
  slurm_id          int8,
  ssh_user          varchar(255),
  state             varchar(255),
  status            varchar(255),
  working_directory varchar(255),

  primary key (system_id)
);