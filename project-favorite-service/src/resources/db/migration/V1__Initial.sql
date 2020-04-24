set search_path to project_favorite;

create sequence hibernate_sequence
    start 1
    increment 1;

create table favorite_project_entity (
    id int8 not null,
    project_id varchar(255),
    the_user varchar(255),
    primary key (id)
);
