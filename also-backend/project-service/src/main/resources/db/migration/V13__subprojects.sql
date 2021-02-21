alter table projects add column parent text references projects;
create unique index project_path_unique on projects (parent, upper(title));
