set search_path to app;
alter table jobs
  add column number_of_nodes int not null default 1;
