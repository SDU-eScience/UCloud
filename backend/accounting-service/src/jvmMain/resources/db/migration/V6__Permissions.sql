create extension if not exists "uuid-ossp" schema public;

alter table provider.resource add column provider_generated_id text unique default null;

create table provider.resource_update(
    id bigserial primary key,
    resource bigint references provider.resource(id),
    created_at timestamptz default now(),
    status text default null,
    extra jsonb not null default '{}'::jsonb
);

create index on provider.resource_update (resource);

drop function if exists provider.resource_to_json(resource_in provider.resource, my_permissions text[], specification jsonb);
drop type if exists provider.accessible_resource cascade;
create type provider.accessible_resource as (
    resource provider.resource,
    product_name text,
    product_category text,
    product_provider text,
    my_permissions text[],
    other_permissions provider.resource_acl_entry[],
    updates provider.resource_update[]
);
drop function if exists provider.resource_to_json(resource_in provider.resource, my_permissions text[], other_permissions provider.resource_acl_entry[], updates provider.resource_update[], specification jsonb);
create or replace function provider.resource_to_json(
    r provider.accessible_resource,
    specification jsonb
) returns jsonb as $$
    select jsonb_build_object(
        'id', (r.resource).id::text,
        'createdAt', (floor(extract(epoch from (r.resource).created_at) * 1000)),
        'owner', jsonb_build_object(
            'createdBy', (r.resource).created_by,
            'project', (r.resource).project
        ),
        'billing', jsonb_build_object(
            'pricePerUnit', 0,
            'creditsCharged', 0
        ),
        'acl', null,
        'status', jsonb_build_object(),
        'permissions', jsonb_build_object(
            'myself', r.my_permissions,
            'others', (
                select jsonb_agg(
                    jsonb_build_object(
                        'type', case
                            when p.group_id is null then 'user'
                            else 'project_group'
                        end,
                        'group', p.group_id,
                        'username', p.username,
                        'projectId', (r.resource).project
                    )
                )
                from unnest(r.other_permissions) p
            )
        ),
        'updates', (
            select coalesce('[]'::jsonb, jsonb_agg(
                jsonb_build_object(
                    'timestamp', (floor(extract(epoch from u.created_at) * 1000)),
                    'status', u.status
                ) || u.extra
            ))
            from unnest(r.updates) u
        ),
        'specification', (
            jsonb_build_object('product', jsonb_build_object(
                'id', r.product_name,
                'category', r.product_category,
                'provider', r.product_provider
            )) || specification
        ),
        'providerGeneratedId', (r.resource).provider_generated_id
    );
$$ language sql;


alter table provider.resource_acl_entry drop column project_id;
alter table provider.resource_acl_entry drop column resource_type;
alter table provider.resource_acl_entry drop column resource_provider;
alter table provider.resource_acl_entry drop column entity_type;
alter table provider.resource_acl_entry add foreign key (group_id) references project.groups;

drop function if exists provider.accessible_resources(username_in text, resource_type_in text);
drop function if exists provider.accessible_resources(username_in text, resource_type_in text, required_permission text);


create index on provider.resource_acl_entry (resource_id);
create index on provider.resource (type);

drop function if exists provider.update_acl(actor_username_in text, resource_id_in text, resource_type_in text, resource_provider_in text, entities_to_add_in text[], entities_to_remove_in text[]);

create or replace function provider.update_acl(
    actor_username_in text,
    resource_id_in text,
    resource_type_in text,
    entities_to_add_in text[],
    entities_to_remove_in text[]
) returns boolean language plpgsql as $$
declare
    has_permission bool;
begin
    select exists(
        select 1
        from provider.accessible_resources(actor_username_in, resource_type_in, 'ADMIN', resource_id_in)
        where (resource).id = resource_id_in
    ) into has_permission;

    if not has_permission then
        return false;
    end if;

    insert into provider.resource_acl_entry
    (group_id, username, resource_id, permission)
    values (
        unnest(entities_to_add_in[0]),
        unnest(entities_to_add_in[1]),
        resource_id,
        unnest(entities_to_add_in[2])
    ) on conflict do nothing;

    delete from provider.resource_acl_entry
    where
        (group_id, username) in (
            select
                unnest(entities_to_remove_in[0]) as entity_type,
                unnest(entities_to_remove_in[1]) as project_id
        ) and
        resource_id = resource_id_in;

    return true;
end
$$;

drop function if exists provider.accessible_resources(username_in text, resource_type_in text, required_permission text, include_others boolean, include_updates boolean);
create or replace function provider.accessible_resources(
    username_in text,
    resource_type_in text,
    required_permission text,
    resource_id bigint = null,
    project_filter text = '', -- empty string is used to indicate no filter, null is used for personal workspaces
    include_others boolean = false,
    include_updates boolean = false
) returns setof provider.accessible_resource language plpgsql stable rows 100 as $EOF$
declare
    query text;
begin
    query = $$
        select distinct
        r,
        the_product.name,
        p_cat.category,
        p_cat.provider,
        array_agg(
            distinct
            case
                when pm.role = 'PI' then 'ADMIN'
                when pm.role = 'ADMIN' then 'ADMIN'
                when r.created_by = $1 and r.project is null then 'ADMIN'
                else acl.permission
            end
        ) as permissions,
    $$;

    if include_others then
        query = query || 'array_remove(array_agg(distinct other_acl), null),';
    else
        query = query || 'array[]::provider.resource_acl_entry[],';
    end if;

    if include_updates then
        -- TODO Fetching all updates might be a _really_ bad idea
        query = query || 'array_remove(array_agg(distinct u), null) as updates';
    else
        query = query || 'array[]::provider.resource_update[]';
    end if;

    query = query || $$
        from
            provider.resource r join
            accounting.products the_product on r.product = the_product.id join
            accounting.product_categories p_cat on the_product.category = p_cat.id left join
            provider.resource_acl_entry acl on r.id = acl.resource_id left join
            project.projects p on r.project = p.id left join
            project.project_members pm on p.id = pm.project_id and pm.username = $1 left join
            project.groups g on pm.project_id = g.project and acl.group_id = g.id left join
            project.group_members gm on g.id = gm.group_id and gm.username = $1
    $$;

    if include_others then
        query = query || ' left join provider.resource_acl_entry other_acl on r.id = other_acl.resource_id';
    end if;

    if include_updates then
        query = query || ' left join provider.resource_update u on r.id = u.resource';
    end if;

    query = query || $$
        where
            ($5 = '' or $5 is not distinct from r.project) and
            ($4::bigint is null or r.id = $4) and
            r.type = $2  and
            (
                (r.created_by = $1 and r.project is null) or
                (acl.username = $1) or
                (pm.role = 'PI' or pm.role = 'ADMIN') or
                (gm.username is not null)
           )
    $$;

    if include_others then
        query = query || ' and other_acl.username is distinct from $1 ';
    end if;

    query = query || $$
        group by r.*, the_product.name, p_cat.category, p_cat.provider
    $$;

    query = query || $$
        having
            array[$3, 'ADMIN'] && array_agg(
                case
                    when pm.role = 'PI' then 'ADMIN'
                    when pm.role = 'ADMIN' then 'ADMIN'
                    when r.created_by = $1 and r.project is null then 'ADMIN'
                    else acl.permission
                end
            );
    $$;

    return query execute query using username_in, resource_type_in, required_permission, resource_id, project_filter;
end;
$EOF$;

create or replace function provider.generate_test_resources() returns void as $$
declare
    current_project text;
    current_group text;
    current_resource bigint;
    the_product bigint;
begin
    select id from accounting.products limit 1 into the_product;
    for current_project in (select id from project.projects tablesample system(40)) loop
        with random_users as (
            select username from project.project_members where project_id = current_project and random() <= 0.4
        )
        insert into provider.resource (type, provider, created_at, created_by, project, product)
        select 'test', null, now(), username, current_project, the_product
        from
            random_users,
            generate_series(0, 50) i;

        for current_resource in (select id from provider.resource where project = current_project) loop
            for current_group in (select id from project.groups where project = current_project and random() <= 0.4) loop
                insert into provider.resource_acl_entry (group_id, username, permission, resource_id)
                values (current_group, null, 'READ', current_resource);
            end loop;
        end loop;

    end loop;
end;
$$ language plpgsql;

create or replace function project.generate_test_project() returns text as $$
declare
    pid text;
    gid text;
begin
    select uuid_generate_v4() into pid;

    insert into project.projects(id, created_at, modified_at, title, parent, dmp) values(
        pid,
        now(),
        now(),
        'My Project: ' || random(),
        null,
        null
    );

    insert into auth.principals (dtype, id, created_at, modified_at, role, first_names, last_name, orc_id, phone_number, title, hashed_password, salt, org_id, email)
    select 'WAYF', 'pi' || pid, now(), now(), 'USER', 'U', 'U', null, null, null, null, null, 'sdu.dk', 'mail' || random() || '@mail.com';

    insert into auth.principals (dtype, id, created_at, modified_at, role, first_names, last_name, orc_id, phone_number, title, hashed_password, salt, org_id, email)
    select 'WAYF', 'user' || pid || i , now(), now(), 'USER', 'U', 'U', null, null, null, null, null, 'sdu.dk', 'mail' || random() || '@mail.com'
    from generate_series(0, 30) i;

    insert into project.project_members (created_at, modified_at, role, username, project_id) values
    (now(), now(), 'PI', 'pi' || pid, pid);

    insert into project.project_members (created_at, modified_at, role, username, project_id)
    select now(), now(), 'USER', 'user' || pid || i, pid
    from generate_series(0, 30) i;

    insert into project.groups (title, project)
    select 'Group' || i, pid
    from generate_series(0, 10) i;

    for gid in (select id from project.groups where project = pid) loop
        with random_users as (
            select username from project.project_members where project_id = pid and random() <= 0.3
        )
        insert into project.group_members (username, group_id)
        select username, gid from random_users;
    end loop;
    return pid;
end;
$$ language plpgsql;
