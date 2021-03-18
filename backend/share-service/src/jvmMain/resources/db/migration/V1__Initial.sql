set search_path to share;

create sequence hibernate_sequence
  start with 1
  increment by 1;

create table shares (
  id              bigint  not null,

  path            varchar(4096),
  filename        varchar(4096),
  file_id         varchar(255),
  link_id         varchar(255),

  owner           varchar(255),
  owner_token     varchar(255),

  shared_with     varchar(255),
  recipient_token varchar(255),

  rights          integer not null,
  state           integer,

  created_at      timestamp,
  modified_at     timestamp,
  primary key (id)
);

create index IDX56jpvu0pf8jvhvtj5ql82sgop
  on shares (file_id);

create index IDXgdqdgg41nyj4jdtvw20d24abb
  on shares (link_id);

alter table shares
  add constraint UKm1pj8jhobbahb24tic22xcndd unique (shared_with, path);
