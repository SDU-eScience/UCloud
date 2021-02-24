create sequence hibernate_sequence start with 1 increment by 1;

create table password_reset_requests
(
    token          varchar(128) not null,
    user_id        varchar(255) not null,
    expires_at     timestamp not null,
    primary key (token)
);
