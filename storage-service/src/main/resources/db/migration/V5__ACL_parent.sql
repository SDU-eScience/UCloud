alter table permissions add column parent varchar(2048);

update permissions
set "parent" = substring("path", 0, length("path") - position('/' in reverse("path")) + 1)
where true;

create index permissions_parent_lookup_index on permissions (parent);
