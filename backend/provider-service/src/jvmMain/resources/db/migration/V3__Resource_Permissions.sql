create table provider.resource(
    id text not null,
    type text not null,
    provider text not null,
    primary key (id, type, provider)
);

create type provider.acl_entity_type as enum ('project_group', 'user');

alter table project.groups add unique (id, project);

drop table if exists provider.resource_acl_entry;
create table provider.resource_acl_entry(
    entity_type provider.acl_entity_type,
    project_id text,
    group_id text,
    username text,
    resource_id text not null,
    resource_type text not null,
    resource_provider text not null,
    permission text not null,
    foreign key (project_id, group_id) references project.groups(project, id),
    foreign key (username) references auth.principals(id),
    foreign key (resource_id, resource_type, resource_provider) references provider.resource(id, type, provider),
    constraint entity_constraint check(
        (username is not null) or
        (project_id is not null and group_id is not null)
    ),
    constraint only_one_entity check(
        (username is null and project_id is not null) or
        (username is not null and project_id is null)
    ),
    constraint type_matches_entity check (
        (entity_type = 'project_group' and project_id is not null) or
        (entity_type = 'user' and username is not null)
    )
);
create unique index acl_entry_unique on provider.resource_acl_entry (
    entity_type,
    coalesce(project_id, ''),
    coalesce(group_id, ''),
    coalesce(username, ''),
    resource_id,
    resource_type,
    resource_provider
);

drop function if exists provider.accessible_resources(text, text);
create or replace function provider.accessible_resources(
    username_in text,
    resource_type_in text
) returns table(
    id text,
    permission text,
    provider text
) as $$ begin
    return query
        select distinct
            r.id,
            case(pm.role)
                when 'PI' then 'ADMIN'
                when 'ADMIN' then 'ADMIN'
                else acl.permission
            end as permission,
            r.provider
        from
            resource r join
            resource_acl_entry acl on
                r.id = acl.resource_id and
                r.type = acl.resource_type and
                r.provider = acl.resource_provider left join
            project.project_members pm on acl.project_id = pm.project_id and pm.username = username_in left join
            project.groups g on pm.project_id = g.project and acl.group_id = g.id left join
            project.group_members gm on g.id = gm.group_id and gm.username = username_in
        where
            r.type = resource_type_in  and
            (
                (acl.username = username_in) or
                (pm.role = 'PI' or pm.role = 'ADMIN') or
                (gm.username is not null)
           );
end $$ language plpgsql;

create or replace function provider.update_acl(
    actor_username_in text,
    resource_id_in text,
    resource_type_in text,
    resource_provider_in text,

    -- [["entity_type", ...], ["project_id", ...], ["group_id", ...], ["username", ...], ["permission", ...]]
    entities_to_add_in text[][],

    -- [["entity_type", ...], ["project_id", ...], ["group_id", ...], ["username", ...]]
    entities_to_remove_in text[][]
) returns bool as $$
declare
    has_permission bool;
begin
    select exists(
        select 1
        from provider.accessible_resources(actor_username_in, resource_type_in)
        where id = resource_id_in and permission = 'ADMIN' and provider = resource_provider_in
    ) into has_permission;

    if not has_permission then
        return false;
    end if;

    insert into provider.resource_acl_entry
    (entity_type, project_id, group_id, username, resource_id, resource_type, resource_provider, permission)
    values (
        unnest(entities_to_add_in[0]::provider.acl_entity_type[]),
        unnest(entities_to_add_in[1]),
        unnest(entities_to_add_in[2]),
        unnest(entities_to_add_in[3]),
        resource_id,
        resource_type,
        resource_provider,
        unnest(entities_to_add_in[4])
    ) on conflict do nothing;

    delete from provider.resource_acl_entry
    where
        (entity_type, project_id, group_id, username) in (
            select
                unnest(entities_to_remove_in[0]) as entity_type,
                unnest(entities_to_remove_in[1]) as project_id,
                unnest(entities_to_remove_in[2]) as group_id,
                unnest(entities_to_remove_in[4]) as username
        ) and
        resource_provider = resource_provider_in and
        resource_type = resource_type_in and
        resource_id = resource_id_in;

    return true;
end $$ language plpgsql;
