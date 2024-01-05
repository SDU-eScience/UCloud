alter table accounting.product_categories add column allow_sub_allocations bool default true;

create or replace function accounting.product_category_to_json(
    pc accounting.product_categories,
    au accounting.accounting_units
) returns jsonb language sql as $$
    select jsonb_build_object(
        'id', pc.id,
        'name', pc.category,
        'provider', pc.provider,
        'accountingFrequency', pc.accounting_frequency,
        'productType', pc.product_type,
        'accountingUnit', json_build_object(
            'name', au.name,
            'namePlural', au.name_plural,
            'floatingPoint', au.floating_point,
            'displayFrequencySuffix', au.display_frequency_suffix
        ),
        'freeToUse', pc.free_to_use,
        'allowSubAllocations', pc.allow_sub_allocations
    );
$$;
