create table provider.notifications(
    id bigserial primary key,
    created_at timestamp not null,
    username varchar(255) not null,
    resource bigint not null
);

alter table provider.notifications add constraint resource_fkey foreign key (resource) references provider.resource(id);
alter table provider.notifications add constraint username_fkey foreign key (username) references auth.principals(id);
