set search_path to accounting_compute;

create table accounting_compute.job_completed_events
(
  job_id              varchar(255) not null,
  application_name    varchar(255),
  application_version varchar(255),
  duration_in_ms      int8         not null,
  nodes               int4         not null,
  started_by          varchar(255),
  timestamp           timestamp,
  primary key (job_id)
);
