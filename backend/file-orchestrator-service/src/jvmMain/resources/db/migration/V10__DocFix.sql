create or replace function file_orchestrator.metadata_document_to_json(
    doc file_orchestrator.metadata_documents
) returns jsonb as
$$
DECLARE approval jsonb;
begin
    approval = jsonb_build_object(
        'type', doc.approval_type
    );

    if doc.approval_type = 'approved' then
        approval = jsonb_set(approval, array['approvedBy']::text[], to_jsonb(doc.approval_updated_by), true);
    elseif doc.approval_type = 'rejected' then
        approval = jsonb_set(approval, array['rejectedBy']::text[], to_jsonb(doc.approval_updated_by), true);
    end if;

    if doc.is_deletion then
        return jsonb_build_object(
            'type', 'deleted',
            'id', doc.id,
            'changeLog', doc.change_log,
            'createdAt', floor(extract(epoch from doc.created_at) * 1000),
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

alter table file_orchestrator.metadata_documents alter column document drop not null;
