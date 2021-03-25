drop table if exists provider.providers;
create table provider.providers
(
    id            text      not null primary key,
    domain        text      not null,
    https         boolean   not null,
    port          int       not null,
    manifest      jsonb     not null,
    created_by    text      not null,
    project       text      not null,
    refresh_token text,
    created_at    timestamp not null default now(),
    acl           jsonb     not null default '[]'::jsonb,
    claim_token   text,
    public_key    text
);
