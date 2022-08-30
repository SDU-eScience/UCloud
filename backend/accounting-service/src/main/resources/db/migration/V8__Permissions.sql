create or replace function provider.accessible_resources(
    username_in text,
    resource_type_in text,
    required_permissions_one_of text[],
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
                when $1 = '#P_' || r.provider then 'PROVIDER'
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
                ($1 = '#P_' || r.provider) or
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
            $3 || array['ADMIN'] && array_agg(
                case
                    when pm.role = 'PI' then 'ADMIN'
                    when pm.role = 'ADMIN' then 'ADMIN'
                    when r.created_by = $1 and r.project is null then 'ADMIN'
                    when $1 = '#P_' || r.provider then 'PROVIDER'
                    else acl.permission
                end
            );
    $$;

    return query execute query using username_in, resource_type_in, required_permissions_one_of, resource_id, project_filter;
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
    query = query || $$from provider.accessible_resources($1, $2, array['READ'], null, $3, $4, $5) r join $$;
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
    query = query || $$from provider.accessible_resources($1, $2, array['PROVIDER', 'READ'], $3, '', $4, $5) r join $$;
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
    permissions_one_of text[] = array['READ'],
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
    return query execute query using user_in, resource_type, resource_ids, include_others,
        include_updates, permissions_one_of;
end;
$EOF$;

drop function if exists provider.default_bulk_retrieve(resource_type text, tbl regclass, to_json regproc, user_in text, resource_ids bigint[], permission text, include_others boolean, include_updates boolean);
drop function if exists provider.accessible_resources(text, text, text, bigint, text, boolean, boolean);
drop function if exists provider.default_browse(text, regclass, text, regproc, text, text);
drop function if exists provider.accessible_resources(text, text, text, bigint, text, boolean, boolean);
