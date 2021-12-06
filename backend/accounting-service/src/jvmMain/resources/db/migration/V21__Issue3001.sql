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
                        balance_available - initial_balance < payment_required or free_to_use = true
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
                        balance_available - initial_balance >= payment_required or free_to_use = true
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
