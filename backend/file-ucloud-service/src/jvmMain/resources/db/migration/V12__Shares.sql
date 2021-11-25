create function convert_share_permissions(req jsonb) returns text[] stable parallel safe cost 1 language sql as $$
    with
        perms as (
            select jsonb_array_elements_text(req) as perm
        ),
        mapped_perms as (
            select case
                when perm = 'READ' then 'READ'
                when perm = 'WRITE' then 'EDIT'
            end as perm
            from perms
        ),
        filtered_perms as (
            select perm
            from mapped_perms
            where perm is not null
        )
    select array(select perm from filtered_perms)
$$;

create temporary table old_shares on commit drop as
with
    processed_shares as (
        select
            s.path,
            array_to_string((regexp_split_to_array(s.path, '/'))[4:cardinality(regexp_split_to_array(s.path, '/'))], '/') as sub_path,
            s.data->>'state' as state,
            convert_share_permissions((s.data->>'rights')::jsonb) as permission,
            s.data->>'sharedWith' as shared_with,
            s.last_modified as created_at,
            row_number() over () as row_number
        from storage.metadata s
        where
            type = 'share' and
            s.data->>'state' = 'ACCEPTED'
    ),
    valid_shares as (
        select
            '/' || r.id || '/' || s.sub_path as path,
            s.state,
            s.shared_with,
            s.created_at,
            s.permission,
            s.row_number,
            r.id as resource_id,
            r.created_by
        from
            processed_shares s,
            file_orchestrator.file_collections c join
            provider.resource r on c.resource = r.id
        where
            r.provider_generated_id = 'h-' || (regexp_split_to_array(s.path, '/'))[3]
    ),
    user_validated as (
        select s.*
        from
            valid_shares s join
            auth.principals shared_by on s.created_by = shared_by.id join
            auth.principals shared_with on s.shared_with = shared_with.id
    )
select * from user_validated;

drop function convert_share_permissions(req jsonb);

with
    product_id as (
        select p.id
        from
            accounting.products p join
            accounting.product_categories pc on p.category = pc.id
        where
            p.name = 'share' and pc.category = 'u1-cephfs' and pc.provider = 'ucloud'
        limit 1
    ),
    share_resources as (
        insert into provider.resource (type, provider, created_at, created_by, project, product, provider_generated_id, confirmed_by_provider, public_read)
        select
            'share',
            'ucloud',
            s.created_at,
            s.created_by,
            null,
            pid.id,
            '' || s.row_number,
            true,
            false
        from
            old_shares s,
            product_id pid
        returning id, provider_generated_id
    ),
    share_permissions as (
        insert into provider.resource_acl_entry (group_id, username, permission, resource_id)
        select null, s.shared_with, 'READ', r.id
        from
            old_shares s join
            share_resources r on s.row_number::bigint = r.provider_generated_id::bigint
        returning  resource_id
    )
insert into file_orchestrator.shares (resource, shared_with, permissions, original_file_path, available_at, state)
select r.id, s.shared_with, s.permission, s.path, null, 'APPROVED'
from
    old_shares s join
    share_resources r on s.row_number::bigint = r.provider_generated_id::bigint;

with
    product_id as (
        select p.id
        from
            accounting.products p join
            accounting.product_categories pc on p.category = pc.id
        where
            p.name = 'share' and pc.category = 'u1-cephfs' and pc.provider = 'ucloud'
        limit 1
    ),
    collections as (
        insert into provider.resource (type, provider, created_at, created_by, project, product, provider_generated_id, confirmed_by_provider, public_read)
        select 'file_collection', 'ucloud', s.created_at, '_ucloud', null, pid.id, 's-' || r.id, true, false
        from
            old_shares s join
            provider.resource r on s.row_number::text = r.provider_generated_id,
            product_id pid
        returning id, provider_generated_id
    ),
    file_collections as (
        insert into file_orchestrator.file_collections (resource, title)
        select c.id, (regexp_split_to_array(s.path, '/'))[cardinality(regexp_split_to_array(s.path, '/'))]
        from
            old_shares s join
            provider.resource share_resource on s.row_number::text = share_resource.provider_generated_id join
            collections c on c.provider_generated_id = 's-' || share_resource.id
    )
insert into provider.resource_acl_entry (group_id, username, permission, resource_id)
select null, s.shared_with, unnest(s.permission), c.id
from
    old_shares s join
    provider.resource share_resource on s.row_number::text = share_resource.provider_generated_id join
    collections c on c.provider_generated_id = 's-' || share_resource.id;

update file_orchestrator.shares s
set available_at = '/' || r.id
from provider.resource r
where r.provider_generated_id = 's-' || s.resource;
