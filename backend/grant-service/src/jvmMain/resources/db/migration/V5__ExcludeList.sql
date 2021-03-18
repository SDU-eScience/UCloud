create table exclude_applications_from(
    project_id text not null,
    email_suffix text default null,
    primary key (project_id, email_suffix)
);
