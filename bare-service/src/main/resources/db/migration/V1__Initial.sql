set search_path to bare;

create sequence hibernate_sequence
  start 1
  increment 1;

create table sample_entities (
  id       int8 not null,
  contents varchar(255),
  primary key (id)
);