set search_path to activity;

-- The old SQL code was BAD. We just delete the whole thing.
drop table if exists activity_stream_entries_file_references;
drop table if exists activity_stream_entries_counted_entries;
drop table if exists counted_entries;
drop table if exists file_references;
drop table if exists activity_stream_entries;
drop table if exists activity_events;

create table activity_events (
  dtype     varchar(31) not null,
  id        bigint      not null,
  file_id   varchar(255),
  timestamp timestamp,
  new_name  varchar(255),
  username  varchar(255),
  favorite  boolean,
  primary key (id)
);

create table activity_stream_entries (
  dtype        varchar(31)  not null,
  id           varchar(255) not null,
  operation    varchar(255) not null,
  subject_type integer      not null,
  timestamp    timestamp    not null,
  primary key (id, operation, subject_type, timestamp)
);

create table counted_entries (
  id                 bigint  not null,
  count              integer not null,
  file_id            varchar(255),
  entry_id           varchar(255),
  entry_operation    varchar(255),
  entry_subject_type integer,
  entry_timestamp    timestamp,
  primary key (id)
);

create table entry_users (
  id                 bigint not null,
  username           varchar(255),
  entry_id           varchar(255),
  entry_operation    varchar(255),
  entry_subject_type integer,
  entry_timestamp    timestamp,
  primary key (id)
);

create table file_references (
  id                 bigint not null,
  file_id            varchar(255),
  entry_id           varchar(255),
  entry_operation    varchar(255),
  entry_subject_type integer,
  entry_timestamp    timestamp,
  primary key (id)
);

create index IDXmqya7nelbfe1n6wivkxgp5n15
  on activity_events (file_id);

alter table counted_entries
  add constraint FKnf8hflr3g6fvtt29jj1iexlmd
foreign key (entry_id, entry_operation, entry_subject_type, entry_timestamp)
references activity_stream_entries;

alter table entry_users
  add constraint FKnexsye420lt6hawodajhcbphb
foreign key (entry_id, entry_operation, entry_subject_type, entry_timestamp)
references activity_stream_entries;

alter table file_references
  add constraint FKin78v4ccneylmh91481pfosro
foreign key (entry_id, entry_operation, entry_subject_type, entry_timestamp)
references activity_stream_entries;
