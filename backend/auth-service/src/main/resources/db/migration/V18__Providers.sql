create table auth.providers
(
    id            text      not null primary key,
    pub_key       text      not null,
    priv_key      text      not null,
    refresh_token text      not null,
    claim_token   text      not null unique,
    did_claim     boolean   not null default false,
    created_at    timestamp not null default now()
);
