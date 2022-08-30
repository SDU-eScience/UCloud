set search_path to notification;

create sequence hibernate_sequence
  start 1
  increment 1;

create table notifications (
  id          int8    not null,
  created_at  timestamp,
  message     varchar(255),
  meta        jsonb,
  modified_at timestamp,
  owner       varchar(255),
  read        boolean not null,
  type        varchar(255),
  primary key (id)
);

create index on notifications (type);
create index on notifications (created_at);