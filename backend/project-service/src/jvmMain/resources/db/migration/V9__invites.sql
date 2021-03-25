create table invites(
    project_id text not null,
    username text not null,
    invited_by text not null,
    created_at timestamp default now(),

    primary key (project_id, username),
    foreign key (project_id) references projects on delete cascade
);