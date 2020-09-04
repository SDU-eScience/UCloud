create or replace function group_member_cleanup() returns trigger as $$
begin
    delete from group_members where username = old.username and group_id in
        (select id from groups where project = old.project_id);
    return null;
end;
$$ language plpgsql;

create trigger group_member_cleanup_trigger after delete on project_members for each row execute procedure group_member_cleanup();
