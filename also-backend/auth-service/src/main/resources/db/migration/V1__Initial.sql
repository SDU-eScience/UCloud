set search_path to auth;

create sequence hibernate_sequence
  start 1
  increment 1;

create table ott_black_list (
  claimed_by varchar(255),
  jti        varchar(255) not null,
  primary key (jti)
);

create table principals (
  dtype           varchar(31)  not null,
  id              varchar(255) not null,
  created_at      timestamp,
  modified_at     timestamp,
  role            varchar(255),
  first_names     varchar(255),
  last_name       varchar(255),
  orc_id          varchar(255),
  phone_number    varchar(255),
  title           varchar(255),
  hashed_password bytea,
  salt            bytea,
  org_id          varchar(255),
  primary key (id)
);

create table refresh_tokens (
  token              varchar(255) not null,
  associated_user_id varchar(255),
  primary key (token)
);

alter table if exists refresh_tokens
  add constraint FKpsuddehavklxsnx2i22ybk1y6
foreign key (associated_user_id)
references principals;