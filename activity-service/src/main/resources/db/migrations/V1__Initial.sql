set search_path to activity;

create sequence hibernate_sequence
  start with 1
  increment by 1;

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

create table activity_stream_entries_counted_entries (
  hactivity_stream_entry$counted_id           varchar(255) not null,
  hactivity_stream_entry$counted_operation    varchar(255) not null,
  hactivity_stream_entry$counted_subject_type integer      not null,
  hactivity_stream_entry$counted_timestamp    timestamp    not null,
  entries_id                                  bigint       not null
);

create table activity_stream_entries_file_references (
  hactivity_stream_entry$tracked_id           varchar(255) not null,
  hactivity_stream_entry$tracked_operation    varchar(255) not null,
  hactivity_stream_entry$tracked_subject_type integer      not null,
  hactivity_stream_entry$tracked_timestamp    timestamp    not null,
  file_ids_id                                 bigint       not null,
  primary key (hactivity_stream_entry$tracked_id, hactivity_stream_entry$tracked_operation, hactivity_stream_entry$tracked_subject_type, hactivity_stream_entry$tracked_timestamp, file_ids_id)
);

create table counted_entries (
  id      bigint  not null,
  count   integer not null,
  file_id varchar(255),
  primary key (id)
);

create table file_references (
  id      bigint not null,
  file_id varchar(255),
  primary key (id)
);

create index IDXmqya7nelbfe1n6wivkxgp5n15
  on activity_events (file_id);

alter table activity_stream_entries_counted_entries
  add constraint UK_m63usn3lq1mfxx6sp4eba02m3 unique (entries_id);

alter table activity_stream_entries_file_references
  add constraint UK_7t88ixm6bhi6niwb18wtkk5t6 unique (file_ids_id);

alter table activity_stream_entries_counted_entries
  add constraint FKo4s05cn421nashnakwubsvh2p
foreign key (entries_id)
references counted_entries;

alter table activity_stream_entries_counted_entries
  add constraint FKd04sq01bwhkjcy4na4ldorui4
foreign key (hactivity_stream_entry$counted_id, hactivity_stream_entry$counted_operation, hactivity_stream_entry$counted_subject_type, hactivity_stream_entry$counted_timestamp)
references activity_stream_entries;

alter table activity_stream_entries_file_references
  add constraint FKogkyprcrpv4loietebe2txxe5
foreign key (file_ids_id)
references file_references;

alter table activity_stream_entries_file_references
  add constraint FKelbo8w287miqg7h70rw8chf7
foreign key (hactivity_stream_entry$tracked_id, hactivity_stream_entry$tracked_operation, hactivity_stream_entry$tracked_subject_type, hactivity_stream_entry$tracked_timestamp)
references activity_stream_entries;