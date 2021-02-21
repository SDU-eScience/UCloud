set search_path to notification;

create table notification.subscriptions
(
  id       int8 not null,
  hostname varchar(2048),
  port     int4 not null,
  username varchar(2048),
  primary key (id)
)
