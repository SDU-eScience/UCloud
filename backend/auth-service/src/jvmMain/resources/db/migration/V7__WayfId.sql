set search_path to auth;

alter table principals add column wayf_id varchar(1024) default null;
create index on principals (wayf_id);
