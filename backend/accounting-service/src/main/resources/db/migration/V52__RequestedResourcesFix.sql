create or replace function "grant".application_to_json(app_id_in bigint) returns jsonb
	language sql
as $$
    with max_revision_number as (
        select max(r.revision_number) newest
        from "grant".revisions r
        where r.application_id = app_id_in
        group by application_id
    ),
    max_revision as (
        select *
        from
            max_revision_number n join
            "grant".revisions r on
                r.application_id = app_id_in and
                r.revision_number = n.newest
    ),
    current_revision as (
        select "grant".revision_to_json(
            array_remove(array_agg(("grant".resource_allocation_to_json(rr, pc))), null),
            f,
            r
        ) as result
        from "grant".applications a join
            max_revision mr on mr.application_id = a.id join
            "grant".requested_resources rr on a.id = rr.application_id and
                rr.revision_number = mr.newest join
            "grant".forms f on f.application_id = rr.application_id and
                f.revision_number = mr.newest join
            "grant".revisions r on r.revision_number = mr.newest and
                r.application_id = mr.application_id join
            accounting.product_categories pc on rr.product_category = pc.id
        where a.id = app_id_in
        group by f.*, r.*
    ),
    all_revisions as (
    select array_agg(revisions.results) as revs, app_id_in as appid from (
            select "grant".revision_to_json(
                array_remove(array_agg(("grant".resource_allocation_to_json(rr, pc))), null),
                f,
                r
            ) as results
            from "grant".applications a join
            "grant".revisions r on
                r.application_id = a.id join
            "grant".forms f on
                f.revision_number = r.revision_number and
                f.application_id = r.application_id join
            "grant".requested_resources rr on
                r.application_id = rr.application_id and
                r.revision_number = rr.revision_number join
            accounting.product_categories pc on
                rr.product_category = pc.id
            where a.id = app_id_in
            group by r.*, f.revision_number, f.*
            order by f.revision_number
        ) as revisions
    )
    select jsonb_build_object(
        'id', resolved_application.id,
        'createdAt', (floor(extract(epoch from resolved_application.created_at) * 1000)),
        'updatedAt', (floor(extract(epoch from latest_revision.created_at)  * 1000)),
        'currentRevision', (select result from current_revision limit 1),
        'createdBy', resolved_application.requested_by,
        'status', jsonb_build_object(
            'overallState', resolved_application.overall_state,
            'stateBreakdown', array_remove(array_agg(distinct ("grant".grant_giver_approval_to_json(approval_status))), null),
            'comments', array_remove(array_agg(distinct ("grant".comment_to_json(posted_comment))), null),
            'revisions', revision.revs,
            'projectTitle', p.title,
            'projectPI', coalesce(pm.username,resolved_application.requested_by)
        )
    )
    from
        all_revisions revision join
        "grant".applications resolved_application on revision.appid = resolved_application.id join
        max_revision latest_revision on
            resolved_application.id = latest_revision.application_id join
        "grant".forms latest_form on
            latest_form.revision_number = latest_revision.newest and
            latest_form.application_id = latest_revision.application_id join
        "grant".grant_giver_approvals approval_status on
            resolved_application.id = approval_status.application_id left join
        "grant".comments posted_comment on resolved_application.id = posted_comment.application_id left join
            project.projects p on p.id = latest_form.recipient left join
        project.project_members pm on p.id = pm.project_id and pm.role = 'PI'
    group by
        resolved_application.id,
        resolved_application.*,
        latest_revision.created_at,
        latest_revision.*,
        latest_form.*,
        revision.revs,
        p.title,
        pm.username;
$$;
