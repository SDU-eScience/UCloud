create table application_tags
(
  name    varchar(255) not null,
  version varchar(250) not null,
  tag     varchar(255),
  foreign key (name, version) references applications (name, version)
);

create table favorited_by
(
  name    varchar(255) not null,
  version varchar(250) not null,
  "user"  varchar(255),
  foreign key (name, version) references applications (name, version)
);
