set search_path to metadata;

create sequence hibernate_sequence
  start 1
  increment 1;

create table projects (
  id          int8          not null,
  owner       varchar(255)  not null,
  fs_root     varchar(1024) not null,
  description varchar(255)  not null,

  created_at  timestamp     not null,
  modified_at timestamp     not null,
  primary key (id)
);

alter table if exists projects
  add constraint UK_gm7enxry5wuejxpcgtmqxdmh unique (fs_root);

alter table projects
  add column fs_root_id varchar(256);
alter table projects
  add constraint uk_projects_fs_root_id unique (fs_root_id);
create index on projects (fs_root_id);
