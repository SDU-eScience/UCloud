create table permissions (
    path varchar(2048) not null,
    username varchar(2048) not null,
    permission varchar(255),
    primary key (path, username)
);