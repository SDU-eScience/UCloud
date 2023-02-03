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
            'start', floor(extract(epoch from request_in.start_date)*1000),
            'end', floor(extract(epoch from request_in.end_date)*1000)
        )
    );
$$;
