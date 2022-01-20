create table provider.notifications(
    id bigserial primary key,
    created_at timestamp not null,
    username varchar(255) not null,
    resource bigint not null
);