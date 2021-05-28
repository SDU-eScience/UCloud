alter table project.cooldowns add foreign key (username) references auth.principals(id);
alter table project.cooldowns add foreign key (project) references project.projects(id);
alter table project.group_members add foreign key (username) references auth.principals(id);
alter table project.invites add foreign key (username) references auth.principals(id);
alter table project.invites add foreign key (invited_by) references auth.principals(id);
alter table project.project_favorite add foreign key (username) references auth.principals(id);
alter table project.project_members add foreign key (username) references auth.principals(id);

drop trigger if exists group_member_cleanup_trigger on project.project_members;

create or replace function project.group_member_cleanup() returns trigger as $$
begin
    delete from project.group_members where username = old.username and group_id in
        (select id from project.groups where project = old.project_id);
    return null;
end;
$$ language plpgsql;

create trigger group_member_cleanup_trigger
after delete on project.project_members
for each row execute procedure project.group_member_cleanup();
