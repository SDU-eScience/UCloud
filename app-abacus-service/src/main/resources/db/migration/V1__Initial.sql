set search_path to "app_abacus";

create table jobs (
  system_id varchar(255) not null,
  slurm_id bigint not null,
  primary key (system_id)
);
