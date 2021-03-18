set search_path to file_favorite;

alter table favorites
add column project varchar(4096) default null;
