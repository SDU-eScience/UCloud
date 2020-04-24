alter table permissions add column project varchar(255) default '';
alter table permissions add column project_group varchar(255) default '';
alter table permissions drop constraint permissions_pkey;
alter table permissions rename column entity to username;
alter table permissions add primary key (username, project, project_group, application_name);
alter table permissions alter column username set default '';
alter table permissions drop column entity_type;
