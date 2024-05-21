alter table "grant".forms add column reference_ids text[] default null;

update "grant".forms f
set reference_ids = array[f.reference_id]
where reference_id is not null;

alter table "grant".forms drop column reference_id;

create or replace function "grant".revision_to_json(resources_in jsonb[], form_in "grant".forms, revision_in "grant".revisions) returns jsonb
	language plpgsql
as $$
declare
    builder jsonb;
    document jsonb;
begin

    if form_in.recipient_type = 'personal' then
        document := jsonb_build_object(
            'recipient', jsonb_build_object(
                'type', 'personalWorkspace',
                'username', form_in.recipient
            ),
            'allocationRequests', resources_in,
            'form', "grant".form_to_json(form_in),
            'referenceIds', form_in.reference_ids,
            'revisionComment', revision_in.revision_comment,
            'parentProjectId', form_in.parent_project_id
        );
    elseif form_in.recipient_type = 'existing_project' then
        document := jsonb_build_object(
            'recipient', jsonb_build_object(
                'type', 'existingProject',
                'id', form_in.recipient
            ),
            'allocationRequests', resources_in,
            'form', "grant".form_to_json(form_in),
            'referenceIds', form_in.reference_ids,
            'revisionComment', revision_in.revision_comment,
            'parentProjectId', form_in.parent_project_id

        );
    elseif form_in.recipient_type = 'new_project' then
        document := jsonb_build_object(
            'recipient', jsonb_build_object(
                'type', 'newProject',
                'title', form_in.recipient
            ),
            'allocationRequests', resources_in,
            'form', "grant".form_to_json(form_in),
            'referenceIds', form_in.reference_ids,
            'revisionComment', revision_in.revision_comment,
            'parentProjectId', form_in.parent_project_id
        );
    end if;

    builder := jsonb_build_object(
        'createdAt', (floor(extract(epoch from revision_in.created_at) * 1000)),
        'updatedBy', revision_in.updated_by,
        'revisionNumber', revision_in.revision_number,
        'document', document
    );

    return builder;
end;
$$;
