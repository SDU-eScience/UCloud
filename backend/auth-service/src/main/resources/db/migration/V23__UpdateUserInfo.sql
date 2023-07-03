drop table if exists auth.user_info_update_request;
alter table auth.principals drop column if exists uid;
alter table auth.principals add column uid serial4;
create unique index on auth.principals(uid);

create table auth.user_info_update_request(
    uid int references auth.principals(uid),
    first_names text default null,
    last_name text default null,
    email text default null,
    created_at timestamptz default now(),
    modified_at timestamptz default now(),
    verification_token text unique not null,
    confirmed bool not null default false
);
