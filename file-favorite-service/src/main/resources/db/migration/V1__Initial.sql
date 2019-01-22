set search_path to file_favorite;

create sequence file_favorite.hibernate_sequence
  start 1
  increment 1;

    create table file_favorite.favorites (
       id int8 not null,
        file_id varchar(255),
        username varchar(255),
        primary key (id)
    );

create index on file_favorite.favorites (file_id);
