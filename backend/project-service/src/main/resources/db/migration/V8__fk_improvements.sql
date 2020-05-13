alter table group_members
    drop constraint if exists group_members_project_the_group_fkey;

alter table group_members
    add constraint group_members_project_the_group_fkey
    foreign key (project, the_group)
    references groups(project, the_group)
    on delete cascade;

alter table group_members
    drop constraint if exists group_members_project_members_fkey;

alter table group_members
    add constraint group_members_project_members_fkey
    foreign key (project, username)
    references project_members(project_id, username)
    on delete cascade;

alter table project_members
    drop constraint if exists project_members_project_id_fkey;

alter table project_members
    add constraint project_members_project_id_fkey
    foreign key (project_id)
    references projects(id)
    on delete cascade;
