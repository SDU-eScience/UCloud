set search_path to app;

create table tools
(
  name              varchar(255) not null,
  version           varchar(255) not null,
  created_at        timestamp,
  modified_at       timestamp,
  original_document varchar(65536),
  owner             varchar(255),
  tool              jsonb,
  primary key (name, version)
);

alter table applications
  alter column application type jsonb;

alter table applications
  add column tool_name varchar(255);

alter table applications
  add column tool_version varchar(255);

alter table if exists applications
  add constraint FKd3d72f8m75fv0xlhwwi8nqyvv
    foreign key (tool_name, tool_version)
      references app.tools;
