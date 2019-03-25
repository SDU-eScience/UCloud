set search_path to project_auth;

create table auth_tokens
(
  project       varchar(1024) not null,
  role          varchar(255)  not null,
  refresh_token varchar(1024),
  primary key (project, role)
);
