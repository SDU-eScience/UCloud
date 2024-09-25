alter table task.tasks_v2 add column progress_percentage int not null default -1;
alter table task.tasks_v2 add column icon varchar(64);