set search_path to app;

create table applications (
  name              varchar(255) not null,
  version           varchar(255) not null,
  application       json,
  created_at        timestamp,
  modified_at       timestamp,
  original_document varchar(65536),
  owner             varchar(255),
  primary key (name, version)
)