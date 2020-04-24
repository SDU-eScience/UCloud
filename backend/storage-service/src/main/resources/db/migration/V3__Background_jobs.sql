set search_path to storage;

create table background_jobs
(
  job_id          varchar(255)   not null,
  created_at      timestamp      not null,
  modified_at     timestamp      not null,
  owner           varchar(255)   not null,
  request_message varchar(65536) not null,
  request_type    varchar(255)   not null,
  response        varchar(65536),
  response_code   integer        not null,
  primary key (job_id)
);
