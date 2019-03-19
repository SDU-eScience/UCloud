set search_path to activity;

create index on activity_events ("timestamp");
create index on activity_events ("username");
