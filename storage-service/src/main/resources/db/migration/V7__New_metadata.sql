create table metadata
(
    path text not null,
    path_moving_to text,
    last_modified timestamp not null,
    username text not null,
    type text not null,
    data jsonb not null,

    primary key (path, type, username)
);
