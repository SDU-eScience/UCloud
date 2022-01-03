create or replace function provider.resource_to_json(
    r provider.accessible_resource,
    additional jsonb
) returns jsonb as $$
    select provider.jsonb_merge(
        jsonb_build_object(
            'id', (r.resource).id::text,
            'createdAt', (floor(extract(epoch from (r.resource).created_at) * 1000)),
            'owner', jsonb_build_object(
                'createdBy', (r.resource).created_by,
                'project', (r.resource).project
            ),
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
                ))
            ),
            'providerGeneratedId', (r.resource).provider_generated_id
        ),
        additional
    );
$$ language sql;
