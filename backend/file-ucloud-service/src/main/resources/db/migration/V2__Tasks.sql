drop table if exists file_ucloud.tasks;
create table file_ucloud.tasks
(
    id           text not null primary key,
    request_name text not null,
    requirements jsonb,
    request      jsonb,
    progress     jsonb,
    owner        text not null,
    last_update  timestamp,
    processor_id text default null,
    complete     bool default false
);
