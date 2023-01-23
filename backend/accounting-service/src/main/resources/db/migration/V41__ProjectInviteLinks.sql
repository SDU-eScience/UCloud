create table if not exists project.invite_links (
    token uuid primary key not null,
    project_id text not null references project.projects(id),
    expires timestamptz not null,
    role_assignment text not null default 'USER'
);

create table if not exists project.invite_link_group_assignments (
    link_token uuid not null references project.invite_links(token),
    group_id varchar(255) not null references project.groups(id),
    primary key (link_token, group_id)
);