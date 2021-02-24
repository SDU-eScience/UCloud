create table logos(
    project_id text not null primary key ,
    data bytea
);

create table descriptions(
    project_id text not null primary key ,
    description text
);
