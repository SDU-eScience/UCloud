create table provider.notifications(
    id bigserial primary key,
    time timestamp not null,
    user_id varchar(255) not null,
    provider text not null,
    resource bigint not null,
    permission varchar(255) not null
);