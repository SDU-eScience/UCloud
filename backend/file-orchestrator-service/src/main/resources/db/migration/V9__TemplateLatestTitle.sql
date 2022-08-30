drop type if exists file_orchestrator.metadata_namespace_with_latest_title cascade;
create type file_orchestrator.metadata_namespace_with_latest_title as (
    resource bigint,
    ns_in file_orchestrator.metadata_template_namespaces,
    latest_in file_orchestrator.metadata_templates
);

drop function if exists file_orchestrator.metadata_template_namespace_to_json(ns_in file_orchestrator.metadata_template_namespaces);

create or replace function file_orchestrator.metadata_template_namespace_to_json(
    r_in file_orchestrator.metadata_namespace_with_latest_title
) returns jsonb language sql as $$
    select jsonb_build_object(
        'specification', jsonb_build_object(
            'name', (r_in.ns_in).uname,
            'namespaceType', (r_in.ns_in).namespace_type
        ),
        'status', jsonb_build_object(
            'latestTitle', (r_in.latest_in).title
        )
    );
$$;
