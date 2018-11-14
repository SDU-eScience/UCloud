set search_path to share;

create sequence hibernate_sequence
  start 1
  increment 1;

create table shares (
  id          int8         not null,
  created_at  timestamp,
  modified_at timestamp,
  owner       varchar(255),
  path        varchar(255),
  rights      int4         not null,
  shared_with varchar(255),
  filename    varchar(255) not null,
  state       int4,
  primary key (id)
);

create table tus_upload_entity (
  id          int8 not null,
  created_at  timestamp,
  modified_at timestamp,
  owner       varchar(255),
  progress    int8 not null,
  sensitivity int4,
  size        int8 not null,
  upload_path varchar(255),
  primary key (id)
);

alter table if exists shares
  add constraint UKm1pj8jhobbahb24tic22xcndd unique (shared_with, path);
