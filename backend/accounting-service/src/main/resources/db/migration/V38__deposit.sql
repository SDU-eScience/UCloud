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
            request.application_id application_id,
            case
                when not request.recipient_is_project then false
                else source_alloc.allow_sub_allocations_to_allocate
            end as can_allocate
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
             allocation_path, granted_in, can_allocate, allow_sub_allocations_to_allocate)
        select
            r.idx,
            r.target_wallet,
            r.desired_balance,
            r.desired_balance,
            r.desired_balance,
            r.start_date,
            r.end_date,
            (r.source_allocation_path::text || '.' || r.idx::text)::ltree,
            r.application_id,
            r.can_allocate,
            r.can_allocate
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