alter table accounting.products add column cpu_model text default null;
alter table accounting.products add column gpu_model text default null;
alter table accounting.products add column memory_model text default null;

create or replace function accounting.product_to_json(
    product_in accounting.products,
    category_in accounting.product_categories,
    balance bigint
) returns jsonb language plpgsql as $$
declare
    builder jsonb;
begin
    builder := (
        select jsonb_build_object(
            'category', accounting.product_category_to_json(category_in),
            'pricePerUnit', product_in.price_per_unit,
            'name', product_in.name,
            'description', product_in.description,
            'priority', product_in.priority,
            'version', product_in.version,
            'freeToUse', product_in.free_to_use,
            'productType', category_in.product_type,
            'unitOfPrice', category_in.unit_of_price,
            'chargeType', category_in.charge_type,
            'balance', balance,
            'hiddenInGrantApplications', product_in.hidden_in_grant_applications
        )
    );
    if category_in.product_type = 'STORAGE' then
        builder := builder || jsonb_build_object('type', 'storage');
    end if;
    if category_in.product_type = 'COMPUTE' then
        builder := builder || jsonb_build_object(
            'type', 'compute',
            'cpu', product_in.cpu,
            'gpu', product_in.gpu,
            'memoryInGigs', product_in.memory_in_gigs,
            'cpuModel', product_in.cpu_model,
            'memoryModel', product_in.memory_model,
            'gpuModel', product_in.gpu_model
        );
    end if;
    if category_in.product_type = 'INGRESS' then
        builder := builder || jsonb_build_object('type', 'ingress');
    end if;
    if category_in.product_type = 'LICENSE' then
        builder := builder || jsonb_build_object(
            'type', 'license',
            'tags', product_in.license_tags
        );
    end if;
    if category_in.product_type = 'NETWORK_IP' then
        builder := builder || jsonb_build_object('type', 'network_ip');
    end if;
    if category_in.product_type = 'SYNCHRONIZATION' then
        builder := builder || jsonb_build_object('type', 'synchronization');
    end if;

    return builder;
end
$$;
