-- This script migrates project repositories and home folders into collections
with
    product as (
        select id from accounting.products where name like 'u1-ceph%'
    ),
    users as (
        select id from auth.principals where dtype = 'PASSWORD' or dtype = 'WAYF'
    ),
    resource_ids as (
        insert into provider.resource
            (type, provider, created_at, created_by, project, id, product, provider_generated_id,
             confirmed_by_provider, public_read)
        select
            'file_collection',
            'ucloud',
            now(),
            u.id,
            null,
            nextval('provider.resource_id_seq'),
            p.id,
            'h-' || u.id,
            true,
            false
        from users u, product p
        returning id
    )
insert into file_orchestrator.file_collections (resource, title)
select id, 'Home'
from resource_ids;

with
    product as (
        select id from accounting.products where name like 'u1-ceph%'
    ),
    project_repos as (
        select
            (regexp_split_to_array(path, '/'))[3] as project_id,
            (regexp_split_to_array(path, '/'))[4] as repository_id
        from storage.metadata
        where type = 'project_repository'
    ),
    project_repo_with_pi as (
        select repo.project_id, repo.repository_id, pm.username
        from
            project_repos repo join
            project.project_members pm on
                repo.project_id = pm.project_id and
                pm.role = 'PI'
    ),
    resource_ids as (
        insert into provider.resource
            (type, provider, created_at, created_by, project, id, product, provider_generated_id,
             confirmed_by_provider, public_read)
        select
            'file_collection',
            'ucloud',
            now(),
            repo.username,
            repo.project_id,
            nextval('provider.resource_id_seq'),
            p.id,
            'p-' || repo.project_id || '/' || repo.repository_id,
            true,
            false
        from project_repo_with_pi repo, product p
        returning id, provider_generated_id
    )
insert into file_orchestrator.file_collections (resource, title)
select id, (regexp_split_to_array(provider_generated_id, '/'))[2]
from resource_ids;

insert into provider.resource_acl_entry (group_id, username, permission, resource_id)
with acl_entry as (
    select
        (regexp_split_to_array(path, '/'))[3] as project_id,
        (regexp_split_to_array(path, '/'))[4] as repository_id,
        entry ->> 'group' as group_id,
        jsonb_array_elements_text(entry -> 'permissions') as permission
    from (
        select path, jsonb_array_elements(data->'entries') entry
        from storage.metadata where type = 'project-acl'
    ) entries
)
select
    g.id,
    null,
    case
        when e.permission = 'READ' then 'READ'
        else 'EDIT'
    end,
    r.id
from
    acl_entry e join
    project.groups g on e.group_id = g.id join
    provider.resource r on e.project_id = r.project join
    file_orchestrator.file_collections coll on r.id = coll.resource
where
    coll.title = e.repository_id and
    (
        e.permission = 'READ' or
        e.permission = 'WRITE'
    )
on conflict do nothing;
