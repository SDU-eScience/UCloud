drop function file_orchestrator.metadata_template_to_json(template file_orchestrator.metadata_templates, spec file_orchestrator.metadata_template_specs);
alter table file_orchestrator.metadata_documents drop constraint metadata_documents_template_id_fkey;
alter table file_orchestrator.metadata_documents drop constraint if exists metadata_documents_template_id_template_version_fkey;
delete from file_orchestrator.metadata_documents where true;

alter table file_orchestrator.metadata_documents drop column template_id;
alter table file_orchestrator.metadata_documents drop column template_version;
alter table file_orchestrator.metadata_documents add column template_version text not null;
alter table file_orchestrator.metadata_documents add column template_id bigint not null;

drop table file_orchestrator.metadata_template_specs;
drop table file_orchestrator.metadata_templates;

create type file_orchestrator.metadata_template_namespace_type as enum ('COLLABORATORS', 'PER_USER');

create table file_orchestrator.metadata_template_namespaces(
    resource bigint primary key references provider.resource(id),
    uname text not null,
    namespace_type file_orchestrator.metadata_template_namespace_type not null,
    deprecated boolean default false not null,
    latest_version text default null
);

create unique index on file_orchestrator.metadata_template_namespaces (uname);

create table file_orchestrator.metadata_templates(
    title text not null,
    namespace bigint references file_orchestrator.metadata_template_namespaces(resource),
    uversion text not null,
    schema jsonb not null,
    inheritable boolean not null,
    require_approval boolean not null,
    description text not null,
    change_log text not null,
    ui_schema jsonb,
    deprecated boolean not null default false,
    created_at timestamptz default now(),
    primary key (namespace, uversion)
);

alter table file_orchestrator.metadata_documents add foreign key (template_id, template_version)
    references file_orchestrator.metadata_templates(namespace, uversion);

create or replace function file_orchestrator.metadata_template_namespace_to_json(
    ns_in file_orchestrator.metadata_template_namespaces
) returns jsonb language sql as $$
    select jsonb_build_object(
        'specification', jsonb_build_object(
            'name', ns_in.uname,
            'namespaceType', ns_in.namespace_type
        )
    );
$$;

create or replace function file_orchestrator.metadata_template_to_json(
    ns_in file_orchestrator.metadata_template_namespaces,
    template_in file_orchestrator.metadata_templates
) returns jsonb language sql as $$
    select jsonb_build_object(
        'name', ns_in.uname,
        'title', template_in.title,
        'version', template_in.uversion,
        'schema', template_in.schema,
        'inheritable', template_in.inheritable,
        'requireApproval', template_in.require_approval,
        'description', template_in.description,
        'changeLog', template_in.change_log,
        'uiSchema', template_in.ui_schema,
        'namespaceType', ns_in.namespace_type,
        'createdAt', floor(extract(epoch from template_in.created_at) * 1000)
    );
$$;
