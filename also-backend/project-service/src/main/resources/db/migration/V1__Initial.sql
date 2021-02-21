set search_path to project;

create sequence hibernate_sequence start 1 increment 1;

create table project_members
(
  created_at  timestamp,
  modified_at timestamp,
  role        varchar(255),
  username    varchar(255),
  project_id  varchar(255),
  primary key (username, project_id)
);

create table projects
(
  id          varchar(255) not null,
  created_at  timestamp,
  modified_at timestamp,
  title       varchar(4096),
  primary key (id)
);
