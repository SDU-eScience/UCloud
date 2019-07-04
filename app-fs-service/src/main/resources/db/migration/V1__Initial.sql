create table file_systems
(
    id          varchar(255) not null,
    backend     varchar(255),
    created_at  timestamp,
    modified_at timestamp,
    owner       varchar(1024),
    state       integer,
    primary key (id)
);
