create table project_favorite
(
    project_id text,
    username   text,
    primary key (project_id, username),
    foreign key (project_id) references projects (id) on delete cascade
);

create or replace function is_favorite(uname text, project text)
    returns boolean as
$$
declare
    is_favorite boolean;
begin
    select count(*) > 0
    into is_favorite
    from project_favorite fav
    where fav.username = uname
      and fav.project_id = project;

    return is_favorite;
end;
$$ language plpgsql;

alter table project_members
    add foreign key (project_id) references projects (id) on delete cascade;
