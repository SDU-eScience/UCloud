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
                -- NOTE (HENRIK) balance is changed and should not be subtracted in returning statement
                returning balance as new_balance, alloc.id as local_id
        )
    insert into failed_charges (request_index)
    select local_request_id
    from
        updates u join
        charge_result c on u.local_id = c.local_id
    where u.new_balance < 0 and free_to_use != true;

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
                -- NOTE (HENRIK) local_balance is changed and should not be subtracted again in returning statement
                returning local_balance as new_balance, alloc.id as local_id
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
