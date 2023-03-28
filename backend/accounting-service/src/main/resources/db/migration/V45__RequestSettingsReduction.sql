create function "grant".upload_request_settings(actor_in text, project_in text, new_exclude_list_in text[], new_include_list_type_in text[], new_include_list_entity_in text[]) returns void
	language plpgsql
as $$
declare
    can_update boolean := false;
begin
    if project_in is null then
        raise exception 'Missing project';
    end if;

    select count(*) > 0 into can_update
    from
        project.project_members pm join
        "grant".is_enabled enabled on pm.project_id = enabled.project_id
    where
        pm.username = actor_in and
        (pm.role = 'ADMIN' or pm.role = 'PI') and
        pm.project_id = project_in;

    if not can_update then
        raise exception 'Unable to update this project. Check if you are allowed to perform this operation.';
    end if;

    delete from "grant".exclude_applications_from
    where project_id = project_in;

    insert into "grant".exclude_applications_from (project_id, email_suffix)
    select project_in, unnest(new_exclude_list_in);

    delete from "grant".allow_applications_from
    where project_id = project_in;

    insert into "grant".allow_applications_from (project_id, type, applicant_id)
    select project_in, unnest(new_include_list_type_in), unnest(new_include_list_entity_in);
end;
$$;

drop table "grant".automatic_approval_limits;
drop table "grant".automatic_approval_users;

