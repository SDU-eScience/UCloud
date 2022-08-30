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
                'periods', transaction_in.periods,
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
