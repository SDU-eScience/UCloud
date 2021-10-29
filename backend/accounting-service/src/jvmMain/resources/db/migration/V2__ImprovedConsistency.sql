create temporary table invalid_wallets as (
    select w.account_id, w.account_type
    from
        accounting.wallets w left join
        project.projects p on
            (w.account_id = p.id and w.account_type = 'PROJECT') left join
        auth.principals u on
            (w.account_id = u.id and w.account_type = 'USER')
    where p.id is null and u.id is null
);

delete from accounting.transactions t
using invalid_wallets invalid
where
    t.account_type = invalid.account_type and
    t.account_id = invalid.account_id;

delete from accounting.wallets w
using invalid_wallets invalid
where
    w.account_type = invalid.account_type and
    w.account_id = invalid.account_id;

insert into auth.principals
    (dtype, id, created_at, modified_at, role, first_names, last_name, orc_id, phone_number, title, hashed_password, salt, org_id, email)
values
    ('PASSWORD', 'ghost', now(), now(), 'USER', 'Invalid', 'Invalid', null, null, null, E'\\xDEADBEEF', E'\\xDEADBEEF', null, 'ghost@escience.sdu.dk');

update project.group_members gm
set username = 'ghost'
from
     project.group_members gm2 left join
     auth.principals p on gm2.username = p.id
where
      gm2.username = gm.username and p.id is null;

update project.project_members gm
set username = 'ghost'
from
     project.project_members gm2 left join
     auth.principals p on gm2.username = p.id
where
      gm2.username = gm.username and p.id is null;


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
