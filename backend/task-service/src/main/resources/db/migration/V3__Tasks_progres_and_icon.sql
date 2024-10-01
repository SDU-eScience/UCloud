alter table task.tasks_v2 add column progress_percentage real not null default -1;
alter table task.tasks_v2 add column icon varchar(64);

alter table task.tasks_v2 drop column operation;
alter table task.tasks_v2 add column body text default null;
alter table task.tasks_v2 add column title text default null;