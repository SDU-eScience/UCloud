alter table "grant".requested_resources add source_allocation bigint;

create or replace function approve_application(approved_by text, application_id_in bigint) returns void
	language plpgsql
as $$
declare
    created_project text;
begin
    -- NOTE(Dan): Start by finding all source allocations and target allocation information.
    -- NOTE(Dan): We currently pick source allocation using an "expire first" policy. This might need to change in the
    -- future.
    create temporary table approve_result on commit drop as
    select *
    from (
        select
            approved_by,
            app.id application_id,
            app.grant_recipient,
            app.grant_recipient_type,
            app.requested_by,
            app.resources_owned_by,
            alloc.id allocation_id,
            w.category,
            resource.quota_requested_bytes,
            resource.credits_requested,
            alloc.start_date,
            alloc.end_date,
            row_number() over (partition by w.category order by alloc.end_date nulls last, alloc.id) alloc_idx,
            resource.source_allocation -- Always null or the selected if null we should use the found allocation id based on alloc_idx
        from
            "grant".applications app join
            "grant".requested_resources resource on app.id = resource.application_id join
            accounting.wallet_owner wo on app.resources_owned_by = wo.project_id join
            accounting.wallets w on wo.id = w.owned_by and w.category = resource.product_category join
            accounting.wallet_allocations alloc on
                w.id = alloc.associated_wallet and
                now() >= alloc.start_date and
                (alloc.end_date is null or now() <= alloc.end_date)
        where
            app.status = 'APPROVED' and
            app.id = application_id_in
    ) t
    where
        t.alloc_idx = 1;

    -- NOTE(Dan): Create a project, if the grant_recipient_type = 'new_project'
    insert into project.projects (id, created_at, modified_at, title, archived, parent, dmp, subprojects_renameable)
    select uuid_generate_v4()::text, now(), now(), grant_recipient, false, resources_owned_by, null, false
    from approve_result
    where grant_recipient_type = 'new_project'
    limit 1
    returning id into created_project;

    insert into project.project_members (created_at, modified_at, role, username, project_id)
    select now(), now(), 'PI', requested_by, created_project
    from approve_result
    where grant_recipient_type = 'new_project'
    limit 1;

    -- NOTE(Dan): Run the normal deposit procedure
    perform accounting.deposit(array_agg(req))
    from (
        select (
            result.approved_by,
            case
                when result.grant_recipient_type = 'new_project' then created_project
                when result.grant_recipient_type = 'existing_project' then result.grant_recipient
                else result.grant_recipient
            end,
            case
                when result.grant_recipient_type = 'new_project' then true
                when result.grant_recipient_type = 'existing_project' then true
                else false
            end,
            coalesce(source_allocation, allocation_id), -- see comment regarding source_allocation in approved_result
            coalesce(result.credits_requested, result.quota_requested_bytes),
            result.start_date,
            result.end_date,
            'Grant application approved',
            concat(result.approved_by, '-', uuid_generate_v4()),
            result.application_id
        )::accounting.deposit_request req
        from approve_result result
    ) t;
end;
$$;

alter function approve_application(text, bigint) owner to stolon;


