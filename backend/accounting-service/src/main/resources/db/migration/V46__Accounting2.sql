create table if not exists accounting.accounting_units (
    id bigserial primary key ,
    name text not null,
    name_plural text not null,
    floating_point bool not null,
    display_frequency_suffix bool not null,
    unique (name, name_plural, floating_point, display_frequency_suffix)
);

create table if not exists accounting.accounting_unit_conversions (
    id bigserial not null unique primary key,
    accounting_unit bigint not null,
    product_category bigint not null,
    factor real,
    constraint products_provider_category_fkey
		foreign key (accounting_unit) references accounting.accounting_units,
		foreign key (product_category) references accounting.product_categories
);

alter table accounting.product_categories add column accounting_unit bigint;
alter table accounting.product_categories add column accounting_frequency text;

--Insert Frequency
with insert_units as (
    update accounting.product_categories
    set accounting_frequency = 'ONCE'
    where unit_of_price::text = 'PER_UNIT'
),
insert_minutes as (
    update accounting.product_categories
    set accounting_frequency = 'PERIODIC_MINUTE'
    where unit_of_price::text like '%PER_MINUTE'
),
insert_hours as (
    update accounting.product_categories
    set accounting_frequency = 'PERIODIC_HOUR'
    where unit_of_price::text like '%PER_HOUR'
)
update accounting.product_categories
set accounting_frequency = 'PERIODIC_DAY'
where unit_of_price::text like '%PER_DAY';

--Create AccountingUnits
with dkkinsert as (
    insert into accounting.accounting_units
    (name, name_plural, floating_point, display_frequency_suffix)
    values ('DKK', 'DKK', true, false) returning id
),
update_categories_credits as (
    update accounting.product_categories
    set accounting_unit = dkkinsert.id from dkkinsert
    where postgres.accounting.product_categories.unit_of_price::text like 'CREDITS_PER%'
),
licenseInsert as (
    insert into accounting.accounting_units
    (name, name_plural, floating_point, display_frequency_suffix)
    values ('License', 'Licenses', false, false) returning id
),
update_categories_license as (
    update accounting.product_categories
    set accounting_unit = licenseInsert.id from licenseInsert
    where product_type = 'LICENSE'
),
ingressInsert as (
    insert into accounting.accounting_units
    (name, name_plural, floating_point, display_frequency_suffix)
    values ('Link', 'Links', false, false) returning id
),
update_categories_ingress as (
    update accounting.product_categories
    set accounting_unit = ingressInsert.id from ingressInsert
    where product_type = 'INGRESS'
),
networkInsert as (
    insert into accounting.accounting_units
    (name, name_plural, floating_point, display_frequency_suffix)
    values ('IP', 'IPs', false, false) returning id
),
update_categories_network as (
    update accounting.product_categories
    set accounting_unit = networkInsert.id from networkInsert
    where product_type = 'NETWORK_IP'
),
storageInsert as (
    insert into accounting.accounting_units
    (name, name_plural, floating_point, display_frequency_suffix)
    values ('Gigabyte', 'Gigabytes', false, false) returning id
),
update_categories_storage as (
    update accounting.product_categories
    set accounting_unit = storageInsert.id from storageInsert
    where product_type = 'STORAGE'
),
coreHourInsert as (
    insert into accounting.accounting_units
    (name, name_plural, floating_point, display_frequency_suffix)
    values ('Core hour', 'Core hours', false, true) returning id
)
update accounting.product_categories
set accounting_unit = coreHourInsert.id from coreHourInsert
where unit_of_price::text like 'UNITS_PER%';

alter table accounting.product_categories add foreign key (accounting_unit) references accounting.accounting_units(id);
alter table accounting.products rename column price_per_unit to price;
create or replace function accounting.product_category_to_json(
    pc accounting.product_categories,
    au accounting.accounting_units
) returns jsonb language sql as $$
    select jsonb_build_object(
        'id', pc.id,
        'name', pc.category,
        'provider', pc.provider,
        'accounting_frequency', pc.accounting_frequency,
        'product_type', pc.product_type,
        'accounting_unit', json_build_object(
            'name', au.name,
            'name_plural', au.name_plural,
            'floating_point', au.floating_point,
            'display_frequency_suffix', au.display_frequency_suffix
        )
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
            'freeToUse', product_in.free_to_use,
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

create table if not exists transaction_history(
    id bigserial not null primary key,
    transaction_id text not null,
    created_at timestamp not null,
    affected_allocation bigint not null,
    new_tree_usage bigint,
    new_local_usage bigint,
    new_quota_usage bigint
);

create table if not exists charge_details(
    id bigserial not null primary key,
    transaction_id text not null,
    description text,
    usage bigint,
    product_id text
);


create or replace function accounting.require_fixed_price_per_unit_for_diff_quota() returns trigger language plpgsql as $$
declare
    current_charge_type accounting.charge_type;
begin
    select pc.charge_type into current_charge_type
    from
        accounting.products p join
        accounting.product_categories pc on pc.id = p.category
    where
        p.id = new.id;

    if (current_charge_type = 'DIFFERENTIAL_QUOTA' and new.price != 1) then
        raise exception 'Price per unit for differential_quota products can only be 1';
    end if;
    return null;
end;
$$;


create or replace function accounting.require_fixed_price_per_unit_for_unit_per_x() returns trigger language plpgsql as $$
declare
    current_unit_of_price accounting.product_price_unit;
begin
    select pc.unit_of_price into current_unit_of_price
    from
        accounting.products p join
        accounting.product_categories pc on pc.id = p.category
    where
        p.id = new.id;

    if ((current_unit_of_price = 'UNITS_PER_MINUTE' or
        current_unit_of_price = 'UNITS_PER_HOUR' or
        current_unit_of_price = 'UNITS_PER_DAY' or
        current_unit_of_price = 'PER_UNIT') and
        new.price != 1) then
        raise exception 'Price per unit for UNITS_PER_X or PER_UNIT products can only be 1';
    end if;
    return null;
end;
$$;

create or replace function accounting.require_fixed_price_per_unit_for_free_to_use() returns trigger language plpgsql as $$
begin

    if (new.free_to_use and new.price != 1) then
        raise exception 'Price per unit for free_to_use products can only be 1';
    end if;
    return null;
end;
$$;
