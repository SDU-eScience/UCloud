alter table file_orchestrator.metadata_documents drop column id;
alter table file_orchestrator.metadata_documents add column id serial primary key;
alter table file_orchestrator.metadata_documents drop column template_id cascade;
alter table file_orchestrator.metadata_documents add column template_id bigint;
alter table file_orchestrator.metadata_documents add foreign key (template_id) references file_orchestrator.metadata_template_namespaces(resource);
alter table file_orchestrator.metadata_documents add foreign key (template_id, template_version) references file_orchestrator.metadata_templates(namespace, uversion);

create or replace function file_orchestrator.metadata_template_to_json(
    ns_in file_orchestrator.metadata_template_namespaces,
    template_in file_orchestrator.metadata_templates
) returns jsonb language sql as $$
    select jsonb_build_object(
        'namespaceId', template_in.namespace,
        'namespaceName', ns_in.uname,
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
                'version', doc.template_version,
                'changeLog', doc.change_log
            ),
            'createdAt', floor(extract(epoch from doc.created_at) * 1000),
            'status', jsonb_build_object(
                'approval', approval
            ),
            'createdBy', doc.created_by
        );
    end if;
end;
$$ language plpgsql;

