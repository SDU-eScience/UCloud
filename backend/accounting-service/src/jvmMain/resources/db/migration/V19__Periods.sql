alter table accounting.transactions rename column number_of_products to periods;

alter type accounting.charge_request rename attribute number_of_products to periods;

create or replace function accounting.process_charge_requests(
    requests accounting.charge_request[]
) returns void language plpgsql as $$
declare
    charge_count bigint;
    invalid_transactions bigint;
begin
    create temporary table duplicate_transactions on commit drop as
    with requests as (
        select transaction_id,
               row_number() over () local_request_id
        from unnest(requests) r
    )
    select req.local_request_id, req.transaction_id
    from requests req join accounting.transactions tr on req.transaction_id = tr.transaction_id;

    select count(distinct local_request_id) into invalid_transactions from duplicate_transactions;

    create temporary table product_and_price on commit drop as
    with requests as (
        -- NOTE(Dan): DataGrip/IntelliJ thinks these are unresolved. They are not.
        select payer, payer_is_project, units, periods, product_name, product_cat_name,
               product_provider, performed_by, description, transaction_id,
               row_number() over () local_request_id
        from unnest(requests) r
    )
         -- NOTE(Dan): product_and_price finds the product relevant for this request. It is used later to resolve the
         -- correct price and resolve the correct wallets.
    select
        p.id product_id,
        pc.id product_category,
        pc.charge_type,
        request.units * request.periods * p.price_per_unit as payment_required,
        request.*,
        p.free_to_use
    from
        requests request join
        accounting.products p on request.product_name = p.name join
        accounting.product_categories pc on
                    p.category = pc.id and
                    pc.category = request.product_cat_name and
                    pc.provider = request.product_provider left join
        duplicate_transactions dt on dt.local_request_id = request.local_request_id

    where
            p.version = (
            select max(version)
            from accounting.products p2
            where
                    p2.name = p.name and
                    p2.category = p.category
        ) and
      -- Note(Henrik) The join and null search should filter out the duplicates
        dt.local_request_id is null;

    -- If a request has active allocations with credit remaining, then these must be used
    -- However, if a request only has active allocations with no credit, then one must be picked.
    -- We create a single table of all relevant product_and_price + allocations. Later, we will remove allocations
    -- with a negative balance, but only if we have any allocation with balance remaining.

    create temporary table absolute_allocations on commit drop as
    select *
    from
        product_and_price p left join lateral (
            select
                alloc.id,
                alloc.balance,
                alloc.allocation_path,
                alloc.associated_wallet,
                alloc.end_date
            from
                accounting.wallet_allocations alloc join
                accounting.wallets w on
                            alloc.associated_wallet = w.id and
                            w.category = p.product_category join
                accounting.wallet_owner wo on w.owned_by = wo.id
            where
                    p.charge_type = 'ABSOLUTE' and
                (
                        (payer_is_project and wo.project_id = payer) or
                        (not payer_is_project and wo.username = payer) or
                        (p.free_to_use = true)
                    ) and
                    now() >= alloc.start_date and
                (alloc.end_date is null or now() <= alloc.end_date)
            ) allocations on true;

    delete from absolute_allocations alloc
    where
        (free_to_use != true and balance is null) or
        (
                    free_to_use != true and
                    balance <= 0 and
                    local_request_id in (
                        select alloc.local_request_id from absolute_allocations where balance > 0
                    )
            );

    create temporary table absolute_leaves on commit drop as
        -- NOTE(Dan): leaf_charges determines which leaf allocations to charge and how much can be subtracted from
        -- each allocation.
        --
        -- The code below will attempt to charge as much as possible from the allocations until the
        -- target is reached. If all of the allocations combined do not have enough credits then all allocations will
        -- be emptied. That is, we charge as much as possible. This is useful for the common case, which is that we are
        -- charging for resources which have already been consumed.
        --
        -- A leaf allocation is any allocation that:
        --   1) Matches the product
        --   2) Belongs directly to the payer
    select
        id,
        allocation_path,
        associated_wallet,
        greatest(0, balance - greatest(0, balance - (payment_required - (balance_available - balance)))) as subtracted,
        product_id,
        units, periods, performed_by, description,
        payment_required, local_request_id,
        balance_available,
        free_to_use,
        transaction_id
    from
        (
            select
                product_id, units, periods, performed_by, description, payment_required, local_request_id, transaction_id,
                alloc.id,
                alloc.balance,
                alloc.allocation_path,
                alloc.associated_wallet,
                alloc.free_to_use,
                -- NOTE(Dan): It is very important that we do not have ambiguity in the sort order as this will
                -- cause invalid results, hence we sort by the ID when we have identical end dates.
                sum(greatest(0, alloc.balance)) over (partition by local_request_id order by alloc.end_date nulls last, alloc.id) as balance_available
            from absolute_allocations alloc
            where charge_type = 'ABSOLUTE'
        ) t
    where
        (balance_available - balance < payment_required) or payment_required = 0 or free_to_use = true;

    create temporary table differential_leaves on commit drop as
    with
        -- NOTE(Dan): Similar to absolute products, we first select the allocations which should be used.
        -- Very importantly, we sum over the initial_balance instead of the actual balance!
        allocation_selection as (
            select *
            from
                product_and_price p,
                lateral (
                    select
                        distinct (p.product_id) distinctProductId,
                                 alloc.id,
                                 alloc.balance,
                                 alloc.initial_balance,
                                 alloc.local_balance,
                                 alloc.allocation_path,
                                 alloc.associated_wallet,
                                 -- NOTE(Dan): Notice the use of initial_balance!
                                 sum(alloc.initial_balance) over (order by alloc.end_date nulls last, alloc.id) as balance_available
                    from
                        accounting.wallet_allocations alloc join
                        accounting.wallets w on
                                    alloc.associated_wallet = w.id and
                                    w.category = p.product_category join
                        accounting.wallet_owner wo on w.owned_by = wo.id right join
                        accounting.products p2 on p.product_id = p2.id
                    where
                        (

                                (payer_is_project and wo.project_id = payer) or
                                (not payer_is_project and wo.username = payer) or
                                (p.free_to_use = true)
                            ) and
                        (now() >= alloc.start_date or p.free_to_use = true) and
                        (alloc.end_date is null or now() <= alloc.end_date)
                    ) allocations
            where
                    p.charge_type = 'DIFFERENTIAL_QUOTA'
        ),
        -- NOTE(Dan): For differential products we must consider all allocations that a wallet has. If an allocation
        -- is needed then we should subtract from it. If an allocation is not needed to meet the payment_required
        -- then we must add to it (since we are no longer using this allocation). We first find the once which are
        -- needed, this is very similar to how we do it for ABSOLUTE products.
        subtractions as (
            select
                id,
                allocation_path,
                associated_wallet,
                -- max_requested_from_alloc = balance_available - initial_balance
                -- to_be_used_here = payment_required - max_requested_from_alloc
                local_balance - greatest(0::bigint, initial_balance - (payment_required - (balance_available - initial_balance))) as subtracted,
                product_id,
                units, periods, performed_by, description,
                payment_required, local_request_id,
                balance_available, free_to_use, transaction_id
            from
                allocation_selection
            where
                        balance_available - initial_balance < payment_required or payment_required = 0 or free_to_use = true
        ),
        -- NOTE(Dan): see note above for explanation about additions CTE.
        additions as (
            select
                id,
                allocation_path,
                associated_wallet,
                local_balance - initial_balance as change,
                product_id,
                units, periods, performed_by, description,
                payment_required, local_request_id,
                balance_available, free_to_use, transaction_id
            from
                allocation_selection
            where
                        balance_available - initial_balance >= payment_required or payment_required = 0 or free_to_use = true
        )
        -- NOTE(Dan): Select all rows from subtractions and additions combined.
    select * from subtractions
    union
    select * from additions;

    create temporary table all_leaves on commit drop as
    select * from absolute_leaves
    union
    select * from differential_leaves;

    update all_leaves leaves
    set subtracted = leaves.subtracted + greatest(0, l.payment_required - missing_payment.max_balance)
    from
        (
            select a.*, row_number() over (partition by local_request_id) rn
            from all_leaves a
        ) l join
        (
            -- NOTE(Dan): Recall that balance_available is the rolling sum over multiple allocations. This is why we
            -- are interested in the max, to determine how much has actually been charged.
            select local_request_id, max(balance_available) as max_balance
            from all_leaves
            group by local_request_id
        ) missing_payment on l.local_request_id = missing_payment.local_request_id
    where
            l.id = leaves.id and
            l.rn = 1;

    -- NOTE(Dan): Finally, we combine the leaf allocations with ancestor allocations. We will charge what we subtracted
    -- from the child allocation, in every ancestor.

    create temporary table charge_result on commit drop as
    select
        leaves.id as leaf_id,
        leaves.associated_wallet as leaf_wallet,
        ancestor_alloc.id as local_id,
        subtracted as local_subtraction,
        ancestor_alloc.balance current_balance,
        ancestor_alloc.allocation_path,
        ancestor_alloc.associated_wallet as local_wallet,
        product_id,
        units, periods, performed_by, description,
        payment_required, local_request_id, free_to_use, transaction_id
    from
        all_leaves leaves left join
        accounting.wallet_allocations ancestor_alloc on leaves.allocation_path <@ ancestor_alloc.allocation_path or free_to_use = true;

    create temporary table ancestor_charges on commit drop as
    select leaves.id as leaf_id,
           leaves.associated_wallet as leaf_wallet,
           ancestor_alloc.id as local_id,
           subtracted as local_subtraction,
           ancestor_alloc.balance current_balance,
           ancestor_alloc.allocation_path,
           ancestor_alloc.associated_wallet as local_wallet,
           product_id,
           units, periods, performed_by, description,
           payment_required, local_request_id, free_to_use, transaction_id
    from
        all_leaves leaves left join
        accounting.wallet_allocations ancestor_alloc on leaves.allocation_path <@ ancestor_alloc.allocation_path or free_to_use = true
    where ancestor_alloc.id != leaves.id;

    create temporary table leaves_charges on commit drop as
    select
        leaves.id as leaf_id,
        leaves.associated_wallet as leaf_wallet,
        ancestor_alloc.id as local_id,
        subtracted as local_subtraction,
        ancestor_alloc.balance current_balance,
        ancestor_alloc.allocation_path,
        ancestor_alloc.associated_wallet as local_wallet,
        product_id,
        units, periods, performed_by, description,
        payment_required, local_request_id, free_to_use, transaction_id
    from
        all_leaves leaves left join
        accounting.wallet_allocations ancestor_alloc on leaves.allocation_path <@ ancestor_alloc.allocation_path or free_to_use = true
    where ancestor_alloc.id = leaves.id;

    select count(distinct local_request_id) into charge_count from charge_result;
    if charge_count != (cardinality(requests)-invalid_transactions) then
        raise exception 'Unable to fulfill all requests. Permission denied/bad request.';
    end if;
end;
$$;

-- noinspection SqlResolve
create or replace function accounting.charge(
    requests accounting.charge_request[]
) returns setof int language plpgsql as $$
begin
    -- TODO(Dan): This is currently lacking replay protection
    perform accounting.process_charge_requests(requests);

    create temporary table failed_charges(request_index int) on commit drop;

    -- NOTE(Dan): Update the balance of every relevant allocation
    -- NOTE(Dan): We _must_ sum over the local_subtractions. During the update every read of balance will always
    -- return the same value, thus we would get the wrong results if we don't sum to the total change.
    with
        combined_balance_subtractions as (
            select local_id, sum(local_subtraction) local_subtraction
            from charge_result
            group by local_id
        ),
        updates as (
            update accounting.wallet_allocations alloc
                set balance = balance - local_subtraction
                from combined_balance_subtractions sub
                where alloc.id = sub.local_id
                returning balance - local_subtraction as new_balance, alloc.id as local_id
        )
    insert into failed_charges (request_index)
    select local_request_id
    from
        updates u join
        charge_result c on u.local_id = c.local_id
    where u.new_balance < 0 and free_to_use != true;

    insert into failed_charges (request_index)
    select local_request_id
    from duplicate_transactions;

    with
        combined_local_balance_subtractions as (
            select local_id, sum(case when leaf_id = local_id then local_subtraction else 0 end) local_subtraction
            from charge_result
            group by local_id
            having sum(case when leaf_id = local_id then local_subtraction else 0 end) != 0
        ),
        updates as (
            update accounting.wallet_allocations alloc
                set local_balance = local_balance - local_subtraction
                from combined_local_balance_subtractions sub
                where alloc.id = sub.local_id
                returning local_balance - local_subtraction as new_balance, alloc.id as local_id
        )
    insert into failed_charges (request_index)
    select local_request_id
    from
        updates u join
        charge_result c on u.local_id = c.local_id
    where new_balance < 0 and free_to_use != true;

    delete from ancestor_charges
    where local_request_id in (select local_request_id from failed_charges);

    insert into accounting.transactions
    (type, created_at, affected_allocation_id, action_performed_by, change, description,
     source_allocation_id, product_id, periods, units, transaction_id, initial_transaction_id)
    select 'charge', now(), res.local_id , res.performed_by, -res.local_subtraction, res.description,
           res.leaf_id, res.product_id, res.periods, res.units, res.transaction_id, res.transaction_id from leaves_charges res
    where res.local_subtraction != 0;

    -- NOTE(Dan): Insert a record of every change we did in the transactions table except free to use items
    insert into accounting.transactions
    (type, created_at, affected_allocation_id, action_performed_by, change, description,
     source_allocation_id, product_id, periods, units, transaction_id, initial_transaction_id)
    select 'charge', now(), res.local_id, res.performed_by, -res.local_subtraction, res.description,
           res.leaf_id, res.product_id, res.periods, res.units, uuid_generate_v4(), res.transaction_id
    from ancestor_charges res
    where free_to_use != true;

    return query select distinct request_index - 1 from failed_charges;
end;
$$;

create or replace function accounting.transaction_to_json(
    transaction_in accounting.transactions,
    product_in accounting.products,
    category_in accounting.product_categories
) returns jsonb language plpgsql as $$
declare
    builder jsonb;
begin
    builder := jsonb_build_object(
            'type', transaction_in.type,
            'change', transaction_in.change,
            'actionPerformedBy', transaction_in.action_performed_by,
            'description', transaction_in.description,
            'affectedAllocationId', transaction_in.affected_allocation_id::text,
            'timestamp', floor(extract(epoch from transaction_in.created_at) * 1000),
            'transactionId', transaction_in.transaction_id,
            'initialTransactionId', transaction_in.initial_transaction_id,
            'resolvedCategory', case
                                    when category_in is null then null
                                    else accounting.product_category_to_json(category_in)
                end
        );

    if transaction_in.type = 'charge' then
        builder := builder || jsonb_build_object(
                'sourceAllocationId', transaction_in.source_allocation_id::text,
                'productId', product_in.name,
                'numberOfProducts', transaction_in.periods,
                'units', transaction_in.units
            );
    elseif transaction_in.type = 'deposit' then
        builder := builder || jsonb_build_object(
                'sourceAllocationId', transaction_in.source_allocation_id::text,
                'startDate', floor(extract(epoch from transaction_in.start_date) * 1000),
                'endDate', floor(extract(epoch from transaction_in.end_date) * 1000)
            );
    elseif transaction_in.type = 'transfer' then
        builder := builder || jsonb_build_object(
                'sourceAllocationId', transaction_in.source_allocation_id::text,
                'startDate', floor(extract(epoch from transaction_in.start_date) * 1000),
                'endDate', floor(extract(epoch from transaction_in.end_date) * 1000)
            );
    elseif transaction_in.type = 'allocation_update' then
        builder := builder || jsonb_build_object(
                'startDate', floor(extract(epoch from transaction_in.start_date) * 1000),
                'endDate', floor(extract(epoch from transaction_in.end_date) * 1000)
            );
    end if;

    return builder;
end;
$$;

create or replace function accounting.transfer(
    requests accounting.transfer_request[]
) returns void language plpgsql as $$
begin
    -- TODO(Dan): This is currently lacking replay protection
    perform accounting.process_transfer_requests(requests);

    -- NOTE(Dan): Update the balance of every relevant allocation
    update accounting.wallet_allocations alloc
    set balance = balance - res.local_subtraction
    from transfer_result res
    where alloc.id = res.local_id;

    -- NOTE(Dan): Insert a record of every change we did in the transactions table
    insert into accounting.transactions
    (type, created_at, affected_allocation_id, action_performed_by, change, description,
     source_allocation_id, product_id, periods, units, start_date, end_date, transaction_id, initial_transaction_id)
    select 'transfer', now(), res.local_id, res.performed_by, -res.local_subtraction, res.description,
           res.leaf_id, null, null, null, coalesce(res.start_date, now()), res.end_date, res.transaction_id, res.transaction_id
    from transfer_result res;
end;
$$;
