set search_path to activity;

alter table activity.activity_events add column original_file_name varchar(2048) not null default 'MISSING';
create index on activity.activity_events using hash(original_file_name);


