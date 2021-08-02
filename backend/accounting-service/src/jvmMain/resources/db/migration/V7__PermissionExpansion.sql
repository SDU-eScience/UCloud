create or replace function provider.default_browse(
    resource_type text,
    tbl regclass,
    sort_column text,
    to_json regproc,

    user_in text,
    project_in text
) returns refcursor language plpgsql as $EOF$
declare
    query text;
    c refcursor := 'c';
begin
    query = format('select provider.resource_to_json(r, %s(spec)) ', to_json);
    query = query || $$from provider.accessible_resources($1, $2, 'READ', null, $3) r join $$;
    query = query || format($$%s spec on (r.resource).id = spec.resource $$, tbl);
    query = query || format('order by spec.%I', sort_column);
    raise notice '%', query;
    open c for execute query using user_in, resource_type, project_in;
    return c;
end;
$EOF$;

create or replace function provider.default_browse(
    resource_type text,
    tbl regclass,
    sort_column text,
    to_json regproc,

    user_in text,
    project_in text = '',
    include_others boolean = false,
    include_updates boolean = false
) returns refcursor language plpgsql as $EOF$
declare
    query text;
    c refcursor := 'c';
begin
    query = format('select provider.resource_to_json(r, %s(spec)) ', to_json);
    query = query || $$from provider.accessible_resources($1, $2, 'READ', null, $3, $4, $5) r join $$;
    query = query || format($$%s spec on (r.resource).id = spec.resource $$, tbl);
    query = query || format('order by spec.%I', sort_column);
    open c for execute query using user_in, resource_type, project_in, include_others, include_updates;
    return c;
end;
$EOF$;

create or replace function provider.default_retrieve(
    resource_type text,
    tbl regclass,
    to_json regproc,

    user_in text,
    resource_id bigint,
    include_others boolean = false,
    include_updates boolean = false
) returns setof jsonb language plpgsql as $EOF$
declare
    query text;
begin
    query = format('select provider.resource_to_json(r, %s(spec)) ', to_json);
    query = query || $$from provider.accessible_resources($1, $2, 'READ', $3, '', $4, $5) r join $$;
    query = query || format($$%s spec on (r.resource).id = spec.resource $$, tbl);
    return query execute query using user_in, resource_type, resource_id, include_others, include_updates;
end;
$EOF$;

create or replace function provider.default_bulk_retrieve(
    resource_type text,
    tbl regclass,
    to_json regproc,

    user_in text,
    resource_ids bigint[],
    permission text = 'READ',
    include_others boolean = false,
    include_updates boolean = false
) returns setof jsonb language plpgsql as $EOF$
declare
    query text;
begin
    query = format('select provider.resource_to_json(r, %s(spec)) ', to_json);
    query = query || $$from provider.accessible_resources($1, $2, $6, null, '', $4, $5) r join $$;
    query = query || format($$%s spec on (r.resource).id = spec.resource $$, tbl);
    query = query || 'where spec.resource = some($3) ';
    return query execute query using user_in, resource_type, resource_ids, include_others, include_updates, permission;
end;
$EOF$;

create or replace function project.is_member(
    user_in text,
    project_in text
) returns boolean language sql as $$
    select exists(
        select 1
        from project.projects p join project.project_members pm on p.id = pm.project_id
        where p.id = project_in and pm.username = user_in
    );
$$;

create unique index acl_entry_unique on provider.resource_acl_entry
    (coalesce(username, ''), coalesce(group_id, ''), resource_id);

drop function if exists provider.update_acl(actor_username_in text, resource_id_in text, resource_type_in text, entities_to_add_in text[], entities_to_remove_in text[]);

create or replace function provider.update_acl(
    resource_id_in bigint,

    to_add_groups text[],
    to_add_users text[],
    to_add_permissions text[],

    to_remove_groups text[],
    to_remove_users text[]
) returns void language plpgsql as $$
begin
    with removal_tuples as (
        select unnest(to_remove_groups) as group_id, unnest(to_remove_users) as username
    )
    delete from provider.resource_acl_entry e
    using removal_tuples t
    where
        (t.group_id is not null or t.username is not null) and -- sanity check
        (t.group_id is null or t.group_id = e.group_id) and
        (t.username is null or t.username = e.username) and
        e.resource_id = resource_id_in;

    insert into provider.resource_acl_entry
    (group_id, username, permission, resource_id)
    select unnest(to_add_groups), unnest(to_add_users), unnest(to_add_permissions), resource_id_in
    on conflict (coalesce(username, ''), coalesce(group_id, ''), resource_id)
    do update set permission = excluded.permission;
end;
$$;

create or replace function provider.default_delete(
    tbl regclass,
    resource_ids bigint[]
) returns table(resource bigint) language plpgsql as $EOF$
declare
    query text;
begin
    query = format('delete from %s where resource = some($1) returning resource', tbl);
    return query execute query using resource_ids;
end;
$EOF$;
