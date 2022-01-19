create table provider.notifications(
    id bigserial primary key,
    time timestamp not null,
    event varchar(255) not null,
    user_id varchar(255) not null,
    provider text not null,
    project_id varchar(255),
    group_id varchar(255),
    user_role varchar(255)
);