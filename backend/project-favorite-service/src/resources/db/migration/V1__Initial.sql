set search_path to project_favorite;

create sequence hibernate_sequence
    start 1
    increment 1;

create table project_favorite (
    id int8 not null,
    project_id TEXT,
    the_user TEXT,
    primary key (id)
);
