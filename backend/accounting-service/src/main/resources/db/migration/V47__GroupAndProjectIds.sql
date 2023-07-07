alter table project.projects drop column if exists pid;
alter table project.projects add column pid serial4;
create unique index on project.projects(pid);

alter table project.groups drop column if exists gid;
alter table project.groups add column gid serial4;
create unique index on project.groups(gid);
