delete from file_orchestrator.metadata_documents where true;
delete from file_orchestrator.metadata_templates where true;
delete from file_orchestrator.metadata_template_namespaces where true;
delete from provider.resource where type = 'metadata_template_namespace';

with
    resource as (
        insert into provider.resource
            (type, provider, created_at, created_by, project, product,provider_generated_id, confirmed_by_provider,
            public_read) values
            ('metadata_template_namespace', null, now(), '_ucloud', null, null, null, true, true)
        returning id
    ),
    namespace as (
        insert into file_orchestrator.metadata_template_namespaces (resource, uname, namespace_type, deprecated, latest_version)
        select id, 'favorite', 'PER_USER', false, '1.0.0' from resource
        returning resource
    )
insert into file_orchestrator.metadata_templates
    (title, namespace, uversion, schema, inheritable, require_approval, description, change_log, ui_schema,
    deprecated, created_at)
select
    'Favorite',
    ns.resource,
    '1.0.0',
    '{"type": "object", "title": "favorite", "required": ["favorite"], "properties": {"favorite": {"type": "boolean", "title": "Favorite"}}, "description": "Favorite", "dependencies": {}}'::jsonb,
    false,
    false,
    'favorite',
    'Initial',
    '{"ui:order": ["favorite"]}'::jsonb,
    false,
    now()
from namespace ns;

with
    resource as (
        insert into provider.resource
            (type, provider, created_at, created_by, project, product,provider_generated_id, confirmed_by_provider,
            public_read) values
            ('metadata_template_namespace', null, now(), '_ucloud', null, null, null, true, true)
        returning id
    ),
    namespace as (
        insert into file_orchestrator.metadata_template_namespaces (resource, uname, namespace_type, deprecated, latest_version)
        select id, 'sensitivity', 'COLLABORATORS', false, '1.0.0' from resource
        returning resource
    )
insert into file_orchestrator.metadata_templates
    (title, namespace, uversion, schema, inheritable, require_approval, description, change_log, ui_schema,
    deprecated, created_at)
select
    'UCloud File Sensitivity',
    ns.resource,
    '1.0.0',
    '{"type": "object", "title": "UCloud File Sensitivity", "required": ["sensitivity"], "properties": {"sensitivity": {"enum": ["SENSITIVE", "CONFIDENTIAL", "PRIVATE"], "type": "string", "title": "File Sensitivity", "enumNames": ["Sensitive", "Confidential", "Private"]}}, "dependencies": {}}'::jsonb,
    false,
    false,
    'Describes the sensitivity of a file.',
    'Initial',
    '{"ui:order": ["sensitivity"]}'::jsonb,
    false,
    now()
from namespace ns;
