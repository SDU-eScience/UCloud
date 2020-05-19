create schema notification;
create sequence notification.hibernate_sequence
  start 1
  increment 1;

create table notification.notifications (
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

create index on notification.notifications (type);
create index on notification.notifications (created_at);
