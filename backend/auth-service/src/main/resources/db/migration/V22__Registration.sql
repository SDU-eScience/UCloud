drop table if exists auth.registration;
create table auth.registration(
    session_id text not null primary key,
    first_names text,
    last_name text,
    email text,
    email_verified bool not null,
    organization text,
    created_at timestamptz not null default now(),
    modified_at timestamptz not null default now(),
    email_verification_token text default null,
    wayf_id text not null
);
create unique index on auth.principals(wayf_id);

drop table if exists auth.verification_email_log;
create table auth.verification_email_log(
    ip_address text not null,
    created_at timestamptz not null default now()
);
create index on auth.verification_email_log (ip_address);
