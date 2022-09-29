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
            'start', floor(extract(epoch from request_in.start_date)),
            'end', floor(extract(epoch from request_in.end_date))
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

drop function if exists "grant".application_to_json(application_in "grant".applications, resources_in jsonb[], resources_owned_by_in project.projects, project_in project.projects, project_pi_in text);
drop function if exists "grant".approve_application(approved_by text, application_id_in bigint);

create or replace function "grant".form_to_json(
    form_in "grant".forms
) returns jsonb
    language plpgsql
as $$
    begin
        return jsonb_build_object(
            'type', 'plain_text',
            'text', form_in.form
        );
    end
$$;

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
                'type', 'personalWorkspace',
                'username', form_in.recipient
            ),
            'allocationRequests', resources_in,
            'form', "grant".form_to_json(form_in),
            'referenceId', form_in.reference_id,
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
            'referenceId', form_in.reference_id,
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

create or replace function "grant".comment_to_json(comment_in "grant".comments) returns jsonb
	immutable
	language sql
as $$
    select case when comment_in is null then null else jsonb_build_object(
        'id', comment_in.id,
        'username', comment_in.posted_by,
        'createdAt', (floor(extract(epoch from comment_in.created_at) * 1000)),
        'comment', comment_in.comment
    ) end
$$;

create or replace function "grant".application_to_json(
    app_id_in bigint
) returns jsonb language sql as $$
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
            array_remove(array_agg(distinct ("grant".resource_allocation_to_json(rr, pc))), null),
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

create or replace function accounting.deposit(requests accounting.deposit_request[]) returns void
	language plpgsql
as $$
declare
    deposit_count bigint;
begin
    create temporary table deposit_result on commit drop as
        -- NOTE(Dan): Resolve and verify source wallet, potentially resolve destination wallet
        with unpacked_requests as (
            select initiated_by, recipient, recipient_is_project, source_allocation, desired_balance, start_date,
                   end_date, description, transaction_id, application_id
            from unnest(requests)
        )
        select
            -- NOTE(Dan): we pre-allocate the IDs to make it easier to connect the data later
            nextval('accounting.wallet_allocations_id_seq') idx,
            source_wallet.id source_wallet,
            source_wallet.category product_category,
            source_alloc.allocation_path source_allocation_path,
            target_wallet.id target_wallet,
            request.recipient,
            request.recipient_is_project,
            coalesce(request.start_date, now()) start_date,
            request.end_date,
            request.desired_balance,
            request.initiated_by,
            source_wallet.category,
            request.description,
            request.transaction_id,
            request.application_id application_id
        from
            unpacked_requests request join
            accounting.wallet_allocations source_alloc on request.source_allocation = source_alloc.id join
            accounting.wallets source_wallet on source_alloc.associated_wallet = source_wallet.id join
            accounting.wallet_owner source_owner on source_wallet.owned_by = source_owner.id left join
            project.project_members pm on
                source_owner.project_id = pm.project_id and
                (pm.role = 'ADMIN' or pm.role = 'PI') and request.initiated_by != '_ucloud' left join
            accounting.wallet_owner target_owner on
                (request.recipient_is_project and target_owner.project_id = request.recipient) or
                (not request.recipient_is_project and target_owner.username = request.recipient) left join
            accounting.wallets target_wallet on
                target_wallet.owned_by = target_owner.id and
                target_wallet.category = source_wallet.category
        where
            (
                request.initiated_by = source_owner.username or
                request.initiated_by = pm.username
            ) or (request.initiated_by = '_ucloud');

    select count(*) into deposit_count from deposit_result;
    if deposit_count != cardinality(requests) then
        raise exception 'Unable to fulfill all requests. Permission denied/bad request.';
    end if;

    -- NOTE(Dan): We don't know for sure that the wallet_owner doesn't exist, but it might not exist since there is
    -- no wallet.
    insert into accounting.wallet_owner (username, project_id)
    select
        case r.recipient_is_project when false then r.recipient end,
        case r.recipient_is_project when true then r.recipient end
    from deposit_result r
    where target_wallet is null
    on conflict do nothing;

    -- NOTE(Dan): Create the missing wallets.
    insert into accounting.wallets (category, owned_by)
    select r.category, wo.id
    from
        deposit_result r join
        accounting.wallet_owner wo on
            (r.recipient_is_project and wo.project_id = r.recipient) or
            (not r.recipient_is_project and wo.username = r.recipient)
    where target_wallet is null
    on conflict do nothing;

    -- NOTE(Dan): Update the result such that all target_wallets are not null
    update deposit_result r
    set target_wallet = w.id
    from
        accounting.wallet_owner wo join
        accounting.wallets w on wo.id = w.owned_by
    where
        w.category = r.category and
        (
            (r.recipient_is_project and wo.project_id = r.recipient) or
            (not r.recipient_is_project and wo.username = r.recipient)
        );

    insert into accounting.deposit_notifications (username, project_id, category_id, balance)
    select
        case
            when r.recipient_is_project = true then null
            else r.recipient
        end,
        case
            when r.recipient_is_project = true then r.recipient
            else null
        end,
        r.category,
        r.desired_balance
    from deposit_result r;

    -- NOTE(Dan): Create allocations and insert transactions
    with new_allocations as (
        insert into accounting.wallet_allocations
            (id, associated_wallet, balance, initial_balance, local_balance, start_date, end_date,
             allocation_path, granted_in)
        select
            r.idx,
            r.target_wallet,
            r.desired_balance,
            r.desired_balance,
            r.desired_balance,
            r.start_date,
            r.end_date,
            (r.source_allocation_path::text || '.' || r.idx::text)::ltree,
            r.application_id
        from deposit_result r
        where r.target_wallet is not null
        returning id, balance
    )
    insert into accounting.transactions
    (type, affected_allocation_id, action_performed_by, change, description, start_date, transaction_id, initial_transaction_id)
    select 'deposit', alloc.id, r.initiated_by, alloc.balance, r.description , now(), r.transaction_id, r.transaction_id
    from
        new_allocations alloc join
        deposit_result r on alloc.id = r.idx;

    update accounting.wallets
    set low_funds_notifications_send = false
    from deposit_result r
    where r.target_wallet = id;
end;
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
            select application_id, max(revision_number) as newest
            from "grant".revisions
            where application_id = application_id_in
            group by application_id
            order by application_id
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
                max_revisions.newest = f.revision_number
            join "grant".requested_resources resource on
                max_revisions.application_id = resource.application_id and
                max_revisions.newest = resource.revision_number
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

    create temporary table grant_created_projects(project_id text primary key) on commit drop;
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


create or replace function "grant".application_status_trigger() returns trigger
	language plpgsql
as $$
begin
    --
    if (
        (new.overall_state = old.overall_state) and
        (new.created_at = old.created_at) and
        (new.requested_by = old.requested_by)
        ) then
            return null;
    end if;
    if old.overall_state = 'APPROVED' or old.overall_state = 'CLOSED' then
        raise exception 'Cannot update a closed application';
    end if;
    return null;
end;
$$;

create or replace function "grant".transfer_application(
    actor_in text,
    application_id_in bigint,
    source_project_id_in text,
    target_project_in text,
    newest_revision_in int,
    revision_comment_in text
) returns void
	language plpgsql
as $$
begin
    if target_project_in = source_project_id_in then
        raise exception 'Unable to transfer application to itself';
    end if;

    with resources_affected as (
        select
            app.id, rr.credits_requested, rr.product_category, rr.start_date, rr.end_date, f.recipient, f.recipient_type, target_project_in as parent_project_id, f.form, f.reference_id, requested_by
        from
            "grant".applications app join
            "grant".revisions rev on
                app.id = rev.application_id and
                rev.revision_number = newest_revision_in join
            "grant".requested_resources rr on
                rr.revision_number = rev.revision_number and
                rr.application_id = app.id and
                rr.grant_giver = source_project_id_in join
            "grant".forms f on
                app.id = f.application_id and
                rev.revision_number = f.revision_number join
            project.project_members pm on
                rr.grant_giver = pm.project_id and
                pm.username = actor_in and
                (pm.role = 'PI' or pm.role = 'ADMIN')
        where
            id = application_id_in
    ),
    update_revision as (
        insert into "grant".revisions (
            application_id, revision_number, created_at, updated_by, revision_comment
        )
        select
            id,
            (newest_revision_in+1),
            now(),
            actor_in,
            revision_comment_in
        from resources_affected
        limit(1)
    ),
    insert_resources as (
        insert into "grant".requested_resources (
            application_id,
            credits_requested,
            quota_requested_bytes,
            product_category,
            source_allocation,
            start_date,
            end_date,
            grant_giver,
            revision_number
        )
        select
            id,
            credits_requested,
            null,
            product_category,
            null,
            start_date,
            end_date,
            target_project_in,
            (newest_revision_in+1)
        from resources_affected
    ),
    update_last_revision_ressources_not_from_source as (
        insert into "grant".requested_resources (
            application_id,
            credits_requested,
            quota_requested_bytes,
            product_category,
            source_allocation,
            start_date,
            end_date,
            grant_giver,
            revision_number
        )
        select
            app.id,
            rr.credits_requested,
            rr.quota_requested_bytes,
            rr.product_category,
            rr.source_allocation,
            rr.start_date,
            rr.end_date,
            rr.grant_giver,
            (newest_revision_in +1)
        from
            "grant".applications app join
            "grant".requested_resources rr on
                rr.revision_number = newest_revision_in and
                rr.application_id = app.id and
                rr.grant_giver != source_project_id_in
        where
            id = application_id_in
    ),
    project_title as (
        select title
        from project.projects
        where id = target_project_in
        limit 1
    ),
    update_grant_givers as (
        update "grant".grant_giver_approvals
        set project_id = target_project_in, project_title = project_title.title
        from project_title
        where
            project_id = source_project_id_in and
            application_id = application_id_in
    )
    insert into "grant".forms (
        application_id,
        revision_number,
        parent_project_id,
        recipient,
        recipient_type,
        form,
        reference_id
    )
    select
        id,
        (newest_revision_in + 1),
        parent_project_id,
        recipient,
        recipient_type,
        form,
        reference_id
    from resources_affected
    limit 1;
end;
$$;


alter table "grant".automatic_approval_limits add column grant_giver text;

create or replace function "grant".upload_request_settings(
    actor_in text,
    project_in text,

    new_exclude_list_in text[],

    new_include_list_type_in text[],
    new_include_list_entity_in text[],

    auto_approve_from_type_in text[],
    auto_approve_from_entity_in text[],
    auto_approve_resource_cat_name_in text[],
    auto_approve_resource_provider_name_in text[],
    auto_approve_credits_max_in bigint[],
    auto_approve_quota_max_in bigint[],
    auto_approve_grant_giver_in text[]
) returns void language plpgsql as $$
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

    delete from "grant".automatic_approval_users
    where project_id = project_in;

    insert into "grant".automatic_approval_users (project_id, type, applicant_id)
    select project_in, unnest(auto_approve_from_type_in), unnest(auto_approve_from_entity_in);

    delete from "grant".automatic_approval_limits
    where project_id = project_in;

    insert into "grant".automatic_approval_limits (project_id, maximum_credits, maximum_quota_bytes, product_category, grant_giver)
    with entries as (
        select
            unnest(auto_approve_resource_cat_name_in) category,
            unnest(auto_approve_resource_provider_name_in) provider,
            unnest(auto_approve_credits_max_in) credits,
            unnest(auto_approve_quota_max_in) quota,
            unnest(auto_approve_grant_giver_in) grant_giver
    )
    select project_in, credits, quota, pc.id, grant_giver
    from entries e join accounting.product_categories pc on e.category = pc.category and e.provider = pc.provider;
end;
$$;
