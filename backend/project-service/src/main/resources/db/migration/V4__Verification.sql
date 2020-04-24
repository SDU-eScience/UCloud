create table project_membership_verification(
    project_id text,
    verification timestamp,
    verified_by text,

    primary key (project_id, verification),
    foreign key (project_id) references projects(id)
);
