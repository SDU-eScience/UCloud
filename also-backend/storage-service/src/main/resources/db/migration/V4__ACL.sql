create table permissions (
    path varchar(2048) not null,
    username varchar(2048) not null,
    permission varchar(255),
    primary key (path, username, permission)
);

create index permissions_lookup_index on permissions (path, username);