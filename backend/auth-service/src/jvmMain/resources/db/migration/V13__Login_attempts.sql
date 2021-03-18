create table login_attempts
(
    id         bigint not null,
    created_at timestamp not null,
    username   varchar(4096),
    primary key (id)
);

create table login_cooldown
(
    id                 bigint  not null,
    allow_logins_after timestamp not null,
    expires_at         timestamp not null,
    severity           integer not null,
    username           varchar(4096) not null,
    primary key (id)
);

create index on login_attempts (username);
create index on login_cooldown (username);
