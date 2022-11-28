create or replace function accounting.wallet_allocation_to_json(
    allocation_in accounting.wallet_allocations
) returns jsonb language sql as $$
    select jsonb_build_object(
        'id', allocation_in.id::text,
        'allocationPath', regexp_split_to_array(allocation_in.allocation_path::text, '\.'),
        'balance', allocation_in.balance,
        'initialBalance', allocation_in.initial_balance,
        'localBalance', allocation_in.local_balance,
        'startDate', floor(extract(epoch from allocation_in.start_date) * 1000),
        'endDate', floor(extract(epoch from allocation_in.end_date) * 1000),
        'grantedIn', allocation_in.granted_in,
        'canAllocate', allocation_in.can_allocate,
        'allowSubAllocationsToAllocate', allocation_in.allow_sub_allocations_to_allocate
    );
$$;
