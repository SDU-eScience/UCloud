alter table project.projects add column if not exists provider_project_for text
    references provider.providers(unique_name) default null;

create or replace function project.project_to_json(
    project_in project.projects,
    groups_in project.groups[],
    group_members_in project.group_members[],
    members_in project.project_members[],
    is_favorite_in bool,
    my_role_in text
) returns jsonb language sql as $$
    select jsonb_build_object(
        'id', project_in.id,
        'createdAt', provider.timestamp_to_unix(project_in.created_at),
        'modifiedAt', provider.timestamp_to_unix(project_in.modified_at),
        'specification', jsonb_build_object(
            'parent', project_in.parent,
            'title', project_in.title,
            'canConsumeResources', project_in.can_consume_resources
        ),
        'status', jsonb_build_object(
            'myRole', my_role_in,
            'archived', project_in.archived,
            'isFavorite', is_favorite_in,
            'members', (
                select array_agg(jsonb_build_object('username', t.username, 'role', t.role))
                from unnest(members_in) as t
            ),
            'settings', jsonb_build_object(
                'subprojects', jsonb_build_object(
                    'allowRenaming', project_in.subprojects_renameable
                )
            ),
            'groups', (
                with
                    groups as (select g from unnest(groups_in) g),
                    group_members as (select gm from unnest(group_members_in) gm),
                    jsonified as (
                        select project.group_to_json(g, array_agg(distinct gm)) js
                        from
                            groups g left join
                            group_members gm on (g.g).id = (gm.gm).group_id
                        where
                            g.g is not null
                        group by
                            g
                    )
                select coalesce(array_remove(array_agg(js), null), array[]::jsonb[])
                from jsonified
            ),
            'personalProviderProjectFor', project_in.provider_project_for
        )
    );
$$;

create table if not exists provider.reverse_connections(
    token text not null primary key,
    provider_id text not null references provider.providers(unique_name),
    created_at timestamptz default now()
);
