alter table project_favorite
    drop constraint if exists project_favorite_project_members_id_fkey;

alter table project_favorite
    add constraint project_favorite_project_members_id_fkey
    foreign key (project_id, username)
    references project_members(project_id, username) on delete cascade;