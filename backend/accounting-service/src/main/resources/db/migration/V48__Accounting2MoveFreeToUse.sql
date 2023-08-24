alter table accounting.product_categories add column free_to_use bool default false;

with all_free as (
    select *
    from accounting.products
    where free_to_use = true
)
update accounting.product_categories pc
set free_to_use = true
from all_free af
where af.category = pc.id;

alter table accounting.products drop column free_to_use;

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
        'freeToUse', pc.free_to_use
);
$$;

create or replace function accounting.product_to_json(
    product_in accounting.products,
    category_in accounting.product_categories,
    unit_in accounting.accounting_units,
    balance bigint
) returns jsonb
	language plpgsql
as $$
declare
    builder jsonb;
begin
    builder := (
        select jsonb_build_object(
            'category', accounting.product_category_to_json(category_in, unit_in),
            'name', product_in.name,
            'description', product_in.description,
            'productType', category_in.product_type,
            'price', product_in.price,
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

drop trigger if exists require_fixed_price_per_unit_for_free on accounting.products;

drop function accounting.require_fixed_price_per_unit_for_free_to_use();


create or replace function accounting.low_funds_wallets(
    name text,
    computeCreditsLimit bigint,
    storageCreditsLimit bigint,
    computeUnitsLimit bigint,
    storageQuotaLimit bigint,
    storageUnitsLimit bigint
) returns void language plpgsql as $$
declare
    query text;
begin
    create temporary table project_wallets on commit drop as
        select
            distinct (wa.id),
            prin.id username,
            wo.project_id,
            pr.title project_title,
            pc.provider,
            pc.category,
            pc.product_type,
            pc.charge_type,
            pc.unit_of_price,
            pc.free_to_use,
            w.low_funds_notifications_send,
            w.id wallet_id,
            wa.local_balance,
            prin.email
        from
            accounting.product_categories pc join
            accounting.products p on pc.id = p.category join
            accounting.wallets w on pc.id = w.category join
            accounting.wallet_allocations wa on w.id = wa.associated_wallet join
            accounting.wallet_owner wo on w.owned_by = wo.id join
            project.projects pr on wo.project_id = pr.id join
            project.project_members pm on pr.id = pm.project_id and (pm.role = 'ADMIN' or pm.role = 'PI') join
            auth.principals prin on prin.id = pm.username
        where wa.start_date <= now()
            and (wa.end_date is null or wa.end_date >= now())
            and w.low_funds_notifications_send = false
            and pc.unit_of_price != 'PER_UNIT'
        order by w.id;

    create temporary table user_wallets on commit drop as
        select
            distinct (wa.id),
            prin.id username,
            wo.project_id,
            null project_title,
            pc.provider,
            pc.category,
            pc.product_type,
            pc.charge_type,
            pc.unit_of_price,
            pc.free_to_use,
            w.low_funds_notifications_send,
            w.id wallet_id,
            wa.local_balance,
            prin.email
        from
            accounting.product_categories pc join
            accounting.products p on pc.id = p.category join
            accounting.wallets w on pc.id = w.category join
            accounting.wallet_allocations wa on w.id = wa.associated_wallet join
            accounting.wallet_owner wo on w.owned_by = wo.id join
            auth.principals prin on prin.id = wo.username
        where wa.start_date <= now()
            and (wa.end_date is null or wa.end_date >= now())
            and w.low_funds_notifications_send = false
            and pc.unit_of_price != 'PER_UNIT'
        order by w.id;

    create temporary table all_wallets on commit drop as
        select * from project_wallets
        union
        select * from user_wallets;

    create temporary table sum_wallets on commit drop as
        select
            wallet_id,
            username,
            project_id,
            project_title,
            email,
            provider,
            category,
            product_type,
            charge_type,
            unit_of_price,
            free_to_use,
            low_funds_notifications_send,
            sum(local_balance)::bigint total_local_balance
        from all_wallets
        group by username, project_id, project_title, wallet_id, provider, category, product_type, charge_type, unit_of_price, free_to_use, low_funds_notifications_send, email;


    create temporary table low_fund_wallets on commit drop as
        SELECT *
        FROM sum_wallets
        where
            (
                product_type = 'STORAGE'
                and (unit_of_price = 'CREDITS_PER_MINUTE'::accounting.product_price_unit or unit_of_price = 'CREDITS_PER_HOUR'::accounting.product_price_unit or unit_of_price = 'CREDITS_PER_DAY'::accounting.product_price_unit)
                and charge_type = 'ABSOLUTE'
                and total_local_balance < storageCreditsLimit
            ) or
            (
                product_type = 'STORAGE'
                and (unit_of_price = 'UNITS_PER_MINUTE'::accounting.product_price_unit or unit_of_price = 'UNITS_PER_HOUR'::accounting.product_price_unit or unit_of_price = 'UNITS_PER_DAY'::accounting.product_price_unit)
                and charge_type = 'ABSOLUTE'
                and total_local_balance < storageUnitsLimit
            ) or
            (
                product_type = 'STORAGE'
                and (unit_of_price = 'UNITS_PER_MINUTE'::accounting.product_price_unit or unit_of_price = 'UNITS_PER_HOUR'::accounting.product_price_unit or unit_of_price = 'UNITS_PER_DAY'::accounting.product_price_unit)
                and charge_type = 'DIFFERENTIAL_QUOTA'
                and total_local_balance < storageQuotaLimit
            ) or
            (
                product_type = 'COMPUTE'
                and (unit_of_price = 'CREDITS_PER_MINUTE'::accounting.product_price_unit or unit_of_price = 'CREDITS_PER_HOUR'::accounting.product_price_unit or unit_of_price = 'CREDITS_PER_DAY'::accounting.product_price_unit)
                and charge_type = 'ABSOLUTE'
                and total_local_balance < computeCreditsLimit
            ) or
            (
                product_type = 'COMPUTE'
                and (unit_of_price = 'UNITS_PER_MINUTE'::accounting.product_price_unit or unit_of_price = 'UNITS_PER_HOUR'::accounting.product_price_unit or unit_of_price = 'UNITS_PER_DAY'::accounting.product_price_unit)
                and charge_type = 'ABSOLUTE'
                and total_local_balance < computeUnitsLimit
            );

    query = 'SELECT * FROM low_fund_wallets';
    EXECUTE 'DECLARE ' || quote_ident(name) || ' CURSOR WITH HOLD FOR ' || query;

end;
$$;
