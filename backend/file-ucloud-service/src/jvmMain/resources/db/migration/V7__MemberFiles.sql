insert into accounting.products (name, price_per_unit, cpu, gpu, memory_in_gigs, license_tags, category, free_to_use, description)
select 'project-home', 1, null, null, null, null, pc.id, false, 'Member files for UCloud projects'
from accounting.product_categories pc where pc.provider = 'ucloud' and pc.category = 'u1-cephfs'
on conflict do nothing;

with
    product as (
        select p.id
        from
            accounting.products p join
            accounting.product_categories pc on p.category = pc.id
        where
            p.name = 'project-home' and
            pc.category = 'u1-cephfs' and
            pc.provider = 'ucloud'
    ),
    users as (
        select username, project_id from project.project_members
    ),
    resource_ids as (
        insert into provider.resource
            (type, provider, created_at, created_by, project, id, product, provider_generated_id,
             confirmed_by_provider, public_read)
        select
            'file_collection',
            'ucloud',
            now(),
            u.username,
            u.project_id,
            nextval('provider.resource_id_seq'),
            p.id,
            'pm-' || u.project_id || '/' || u.username,
            true,
            false
        from users u, product p
        returning id, created_by
    )
insert into file_orchestrator.file_collections (resource, title)
select id, 'Member Files: ' || created_by
from resource_ids;

insert into provider.resource_acl_entry (group_id, username, permission, resource_id)
select
    null,
    r.created_by,
    unnest(array['READ', 'EDIT']),
    r.id
from
    provider.resource r
where
    r.type = 'file_collection' and
    r.provider = 'ucloud' and
    r.provider_generated_id like 'pm-%' and
    r.project is not null
on conflict do nothing;
