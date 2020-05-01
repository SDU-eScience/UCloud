set search_path to project_favorite;

create sequence hibernate_sequence
    start 1
    increment 1;

create table project_favorite(
    project_id TEXT,
    username TEXT,
    primary key(project_id, username)
);
