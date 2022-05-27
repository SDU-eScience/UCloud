alter table "grant".requested_resources add source_allocation bigint;
alter table "grant".requested_resources add start_date timestamp default null;
alter table "grant".requested_resources add end_date timestamp;
alter table "grant".requested_resources add grant_giver text references project.projects(id);

--MIGRATE resources_owned_by to requested_resources
update "grant".requested_resources rr
set grant_giver = a.resources_owned_by
from "grant".applications a
where rr.application_id = a.id;

create table "grant".grant_giver_approvals(
    application_id bigint references "grant".applications(id),
    project_id text,
    project_title text,
    state text,
    updated_by text,
    last_update timestamp,
    PRIMARY KEY (application_id, project_id)
);

create table "grant".revisions(
    application_id bigint references "grant".applications(id),
    created_at timestamp,
    updated_by text not null,
    revision_number int not null default 0,
    revision_comment text,
    PRIMARY KEY (application_id, revision_number)
);

alter table "grant".requested_resources add column revision_number int;
alter table "grant".requested_resources add foreign key (application_id,revision_number) references "grant".revisions(application_id, revision_number);

create table "grant".forms(
    application_id bigint references "grant".applications(id),
    revision_number int,
    parent_project_id text,
    recipient text,
    recipient_type text,
    form text,
    reference_id text,
    FOREIGN KEY (application_id, revision_number) references "grant".revisions(application_id, revision_number)
);

-- Migrate applications to new schemas
with old_applications as (
    select *
    from "grant".applications
),
revisions as (
    insert into "grant".revisions (application_id, created_at, updated_by, revision_number, revision_comment)
    select oa.id, oa.created_at, oa.status_changed_by, 0, 'No Comment'
    from old_applications oa
    returning application_id, revision_number
),
applications_with_revisions as (
    select revision_number, grant_recipient, grant_recipient_type, document, reference_id
    from "grant".applications join "grant".revisions r on applications.id = r.application_id
)
insert into "grant".forms (revision_number, recipient, recipient_type, form, reference_id, parent_project_id)
select awr.revision_number, awr.grant_recipient, awr.grant_recipient_type, awr.document, awr.reference_id, oa.resources_owned_by
from applications_with_revisions awr join
    revisions r on
        r.revision_number = awr.revision_number join
    old_applications oa on
        oa.id = r.application_id;

-- Migrate approval states
with old_applications as (
    select a.id app_id, p.id pro_id, p.title, a.status, a.status_changed_by, a.updated_at
    from "grant".applications a join project.projects p on a.resources_owned_by = p.id
)
insert into "grant".grant_giver_approvals(application_id, project_id, project_title, state, updated_by, last_update)
select * from old_applications;

-- DELETE cleanup application table
alter table "grant".applications drop column resources_owned_by;
alter table "grant".applications drop column grant_recipient;
alter table "grant".applications drop column grant_recipient_type;
alter table "grant".applications drop column document;
alter table "grant".applications drop column reference_id;
alter table "grant".applications drop column status_changed_by;
alter table "grant".applications drop column updated_at;
-- rename columns to match new struct
alter table "grant".applications rename status to overall_state;

create or replace function "grant".resource_allocation_to_json(
    request_in "grant".requested_resources,
    product_category_in accounting.product_categories
) returns jsonb language sql as $$
    select jsonb_build_object(
        'category', product_category_in.category,
        'provider', product_category_in.provider,
        'grantGiver', request_in.grant_giver,
        'balanceRequested', request_in.credits_requested,
        'sourceAllocation', request_in.source_allocation,
        'period', jsonb_build_object(
            'start', request_in.start_date,
            'end', request_in.end_date
        )
    );
$$;

create or replace function "grant".grant_giver_approval_to_json(
    grant_giver_approvals_in "grant".grant_giver_approvals
) returns jsonb language sql as $$
    select jsonb_build_object(
        'projectId', grant_giver_approvals_in.project_id,
        'projectTitle', grant_giver_approvals_in.project_title,
        'state', grant_giver_approvals_in.state
    );
$$;

drop function "grant".application_to_json(application_in "grant".applications, resources_in jsonb[], resources_owned_by_in project.projects, project_in project.projects, project_pi_in text);

create or replace function "grant".revision_to_json(
    resources_in jsonb[],
    form_in "grant".forms,
    revision_in "grant".revisions
) returns jsonb
	language plpgsql
as $$
declare
    builder jsonb;
    document jsonb;
begin

    if form_in.recipient_type = 'personal' then
        document := jsonb_build_object(
            'recipient', jsonb_build_object(
                'type', form_in.recipient_type,
                'username', form_in.recipient
            ),
            'allocationRequests', resources_in,
            'form', form_in.form,
            'referenceId', form_in.reference_id,
            'revisionComment', revision_in.revision_comment,
            'parentProjectId', form_in.parent_project_id
        );
    elseif form_in.recipient_type = 'existing_project' then
        document := jsonb_build_object(
            'grantRecipient', jsonb_build_object(
                'type', form_in.recipient_type,
                'id', form_in.recipient
            ),
            'allocationRequests', jsonb_build_array(
                resources_in
            ),
            'form', form_in.form,
            'referenceId', form_in.reference_id,
            'revisionComment', revision_in.revision_comment,
            'parentProjectId', form_in.parent_project_id

        );
    elseif form_in.recipient_type = 'new_project' then
        document := jsonb_build_object(
            'grantRecipient', jsonb_build_object(
                'type', form_in.recipient_type,
                'title', form_in.recipient
            ),
            'allocationRequests', jsonb_build_array(
                resources_in
            ),
            'form', form_in.form,
            'referenceId', form_in.reference_id,
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

create or replace function "grant".application_to_json(
    app_id_in bigint
) returns jsonb language sql as $$
    with maxrevision as (
        select max(r.revision_number) newest, application_id, r.created_at
        from "grant".revisions r
        where r.application_id = app_id_in
        group by application_id, created_at
    ),
    current_revision as (
        select "grant".revision_to_json(
            array_remove(array_agg(distinct ("grant".resource_allocation_to_json(rr, pc))), null),
            f,
            r
        ) as result
        from "grant".applications a join
            maxrevision mr on mr.application_id = a.id join
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
        select "grant".revision_to_json(
            array_remove(array_agg(distinct ("grant".resource_allocation_to_json(rr, pc))), null),
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
        group by r.*, f.*
    )
    select jsonb_build_object(
        'id', a.id,
        'createdAt', a.created_at,
        'updatedAt', mr.created_at,
        'revision', (select result from current_revision limit 1),
        'status', jsonb_build_object(
            'overallState', a.overall_state,
            'stateBreakdown', array_remove(array_agg(distinct ("grant".grant_giver_approval_to_json(gga))), null),
            'comments', array_remove(array_agg(distinct ("grant".comment_to_json(posted_comment))), null),
            'revisions', array_remove(array_agg(distinct (select results from all_revisions)), null)
        )
    )
    from
        "grant".applications a join
        maxrevision mr on
            mr.application_id = a.id join
        "grant".forms f on
            f.revision_number = mr.newest and
            f.application_id = mr.application_id join
        "grant".grant_giver_approvals gga on
            a.id = gga.application_id join
        "grant".comments posted_comment on a.id = posted_comment.application_id
    group by a.id, a.*, mr.created_at, mr.*, f.*
$$;

create or replace function "grant".can_submit_application(username_in text, sources text[], grant_recipient text, grant_recipient_type text) returns boolean
	language sql
as $$
    with
        non_excluded_user as (
            select
                requesting_user.id, requesting_user.email, requesting_user.org_id
            from
                auth.principals requesting_user left join
                "grant".exclude_applications_from exclude_entry on
                    requesting_user.email like '%@' || exclude_entry.email_suffix and
                    exclude_entry.project_id in (select unnest(sources))
            where
                requesting_user.id = username_in
            group by
                requesting_user.id, requesting_user.email, requesting_user.org_id
            having
                count(email_suffix) = 0
        ),
        allowed_user as (
            select user_info.id
            from
                non_excluded_user user_info join
                "grant".allow_applications_from allow_entry on
                    allow_entry.project_id in (select unnest(sources)) and
                    (
                        (
                            allow_entry.type = 'anyone'
                        ) or

                        (
                            allow_entry.type = 'wayf' and
                            allow_entry.applicant_id = user_info.org_id
                        ) or

                        (
                            allow_entry.type = 'email' and
                            user_info.email like '%@' || allow_entry.applicant_id
                        )
                    )
        ),

        existing_project_is_parent as (
            select existing_project.id
            from
                project.projects source_project join
                project.projects existing_project on
                    source_project.id in (select unnest(sources)) and
                    source_project.id = existing_project.parent and
                    grant_recipient_type = 'existing_project' and
                    existing_project.id = grant_recipient join
                project.project_members pm on
                    pm.username = username_in and
                    pm.project_id = existing_project.id and
                    (
                        pm.role = 'ADMIN' or
                        pm.role = 'PI'
                    )
        )
    select coalesce(bool_or(allowed), false)
    from (
        select true allowed
        from
            allowed_user join
            "grant".is_enabled on
                is_enabled.project_id in (select unnest(sources))
        where allowed_user.id is not null

        union

        select true allowed
        from existing_project_is_parent
    ) t
$$;

create or replace function "grant".approve_application(application_id_in bigint, parent_project_id_in text) returns void
	language plpgsql
as $$
declare
    created_project text;
begin
    -- NOTE(Dan): Start by finding all source allocations and target allocation information.
    -- NOTE(Dan): We currently pick source allocation using an "expire first" policy. This might need to change in the
    -- future.
    create temporary table approve_result on commit drop as
        with max_revisions as (
            select b.* from (
                select application_id, max(revision_number) as newest
                from "grant".revisions
                where application_id = application_id_in
                group by application_id
                order by application_id
            ) as a join "grant".revisions b on a.newest = b.revision_number
        )
        select
            app.id application_id,
            app.requested_by,
            f.recipient,
            f.recipient_type,
            resource.grant_giver,
            resource.source_allocation,
            alloc.id allocation_id,
            resource.credits_requested,
            alloc.start_date,
            alloc.end_date
        from
            max_revisions
            join "grant".applications app on
                max_revisions.application_id = app.id
            join "grant".forms f on
                app.id = f.application_id and
                max_revisions.revision_number = f.revision_number
            join "grant".requested_resources resource on
                max_revisions.application_id = resource.application_id and
                max_revisions.revision_number = resource.revision_number
            join accounting.wallet_allocations alloc on
                resource.source_allocation = alloc.id
            join accounting.wallet_owner wo on
                resource.grant_giver = wo.project_id
        where
            app.overall_state = 'APPROVED' and
            app.id = application_id_in;


    -- NOTE(Dan): Create a project, if the grant_recipient_type = 'new_project'
    insert into project.projects (id, created_at, modified_at, title, archived, parent, dmp, subprojects_renameable)
    select uuid_generate_v4()::text, now(), now(), recipient, false, parent_project_id_in, null, false
    from approve_result
    where recipient_type = 'new_project'
    limit 1
    returning id into created_project;

    insert into project.project_members (created_at, modified_at, role, username, project_id)
    select now(), now(), 'PI', requested_by, created_project
    from approve_result
    where recipient_type = 'new_project'
    limit 1;

    create temporary table grant_created_projects(project_id text primary key);
    insert into grant_created_projects(project_id) select created_project where created_project is not null;

    -- NOTE(Dan): Run the normal deposit procedure
    perform accounting.deposit(array_agg(req))
    from (
        select (
            '_ucloud',
            case
                when result.recipient_type = 'new_project' then created_project
                when result.recipient_type = 'existing_project' then result.recipient
                else result.recipient
            end,
            case
                when result.recipient_type = 'new_project' then true
                when result.recipient_type = 'existing_project' then true
                else false
            end,
            allocation_id,
            result.credits_requested,
            result.start_date,
            result.end_date,
            'Grant application approved',
            concat('_ucloud', '-', uuid_generate_v4()),
            result.application_id
        )::accounting.deposit_request req
        from approve_result result
    ) t;
end;
$$;




