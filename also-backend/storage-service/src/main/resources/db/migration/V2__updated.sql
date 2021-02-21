set search_path to storage;

alter table shares
  add filename varchar(255) not null ;
