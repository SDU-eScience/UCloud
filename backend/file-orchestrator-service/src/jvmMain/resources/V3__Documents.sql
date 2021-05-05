drop table if exists file_orchestrator.metadata_documents;
create table file_orchestrator.metadata_documents
(
    id                   text      not null primary key,
    path                 text      not null,
    parent_path          text      not null,

    template_id          text      not null references file_orchestrator.metadata_templates (id),
    template_version     text      not null,

    is_deletion          bool,
    document             jsonb     not null,
    change_log           text      not null,

    created_by           text      not null,
    workspace            text      not null,
    is_workspace_project bool,

    latest               bool,

    approval_type        text      not null,
    approval_updated_by  text               default null,

    created_at           timestamp not null default now(),

    foreign key (template_id, template_version) references file_orchestrator.metadata_template_specs (template_id, version)
);

create or replace function file_orchestrator.metadata_document_to_json(
    doc file_orchestrator.metadata_documents
) returns jsonb as
$$
DECLARE approval json;
begin
    approval = jsonb_build_object(
        'type', doc.approval_type
    );

    if doc.approval_type = 'approved' then
        approval = jsonb_set(approval, '{approvedBy}', to_jsonb(doc.approval_updated_by));
    elseif doc.approval_type = 'rejected' then
        approval = jsonb_set(approval, '{rejectedBy}', to_jsonb(doc.approval_updated_by));
    end if;

    if doc.is_deletion then
        return jsonb_build_object(
            'type', 'deleted'
            'changeLog', doc.change_log,
            'createdAt', doc.created_at,
            'createdBy', doc.created_by,
            'status', jsonb_build_object(
                'approval', approval
            )
        );
    else
        return jsonb_build_object(
            'type', 'metadata',
            'id', doc.id,
            'specification', jsonb_build_object(
                'templateId', doc.template_id,
                'document', doc.document,
                'changeLog', doc.change_log
            ),
            'createdAt', floor(extract(epoch from doc.created_at) * 1000),
            'status', jsonb_build_object(
                'approval', approval
            ),
            'updates', '[]'::jsonb,
            'owner', jsonb_build_object(
                'createdBy', doc.created_by,
                'project', (case (doc.is_workspace_project)
                    when true then doc.workspace
                    end
                )
            )
        );
    end if;
end;
$$ language plpgsql;


create or replace function file_orchestrator.metadata_template_to_json(
    template file_orchestrator.metadata_templates,
    spec file_orchestrator.metadata_template_specs
) returns jsonb as
$$
begin
    return jsonb_build_object(
        'id', template.id,
        'specification', jsonb_build_object(
            'id', spec.template_id,
            'title', spec.title,
            'version', spec.version,
            'schema', spec.schema,
            'inheritable', spec.inheritable,
            'requireApproval', spec.require_approval,
            'description', spec.description,
            'changeLog', spec.change_log,
            'namespaceType', spec.namespace_type,
            'uiSchema', spec.ui_schema
        ),
        'status', jsonb_build_object(
            'oldVersions', '[]'::jsonb
        ),
        'updates', '[]'::jsonb,
        'owner', jsonb_build_object(
            'createdBy', template.created_by,
            'project', template.project
        ),
        'acl', template.acl,
        'createdAt', floor(extract(epoch from template.created_at) * 1000),
        'public', template.is_public
    );
end;
$$ language plpgsql;

create index latest_update_idx on file_orchestrator.metadata_documents (path, latest, workspace, is_workspace_project);
create index browse_idx on file_orchestrator.metadata_documents (parent_path, workspace, is_workspace_project);
create index path_idx on file_orchestrator.metadata_documents (path, workspace, is_workspace_project);
