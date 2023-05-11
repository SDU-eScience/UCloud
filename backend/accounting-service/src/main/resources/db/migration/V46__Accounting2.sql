create table if not exists accounting.accounting_units (
    id bigserial not null unique primary key,
    name text not null,
    name_plural text not null,
    floating_point bool not null,
    display_frequency_suffix bool not null
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
    where unit_of_price = 'PER_UNIT'
),
insert_minutes as (
    update accounting.product_categories
    set accounting_frequency = 'PERIODIC_MINUTE'
    where unit_of_price like '%PER_MINUTE'
),
insert_hours as (
    update accounting.product_categories
    set accounting_frequency = 'PERIODIC_HOUR'
    where unit_of_price like '%PER_HOUR'
)
update accounting.product_categories
set accounting_frequency = 'PERIODIC_DAY'
where unit_of_price like '%PER_DAY';

--Create AccountingUnits
with dkkinsert as (
    insert into accounting.accounting_units
    (name, name_plural, floating_point, display_frequency_suffix)
    values ('DKK', 'DKK', true, false) returning id
),
update_categories_credits as (
    update accounting.product_categories
    set accounting_unit = id from dkkinsert
    where postgres.accounting.product_categories.unit_of_price like 'CREDITS_PER%'
),
licenseInsert as (
    insert into accounting.accounting_units
    (name, name_plural, floating_point, display_frequency_suffix)
    values ('License', 'Licenses', false, false) returning id
),
update_categories_license as (
    update accounting.product_categories
    set accounting_unit = id from licenseInsert
    where product_type = 'LICENSE'
),
ingressInsert as (
    insert into accounting.accounting_units
    (name, name_plural, floating_point, display_frequency_suffix)
    values ('Link', 'Links', false, false) returning id
),
update_categories_ingress as (
    update accounting.product_categories
    set accounting_unit = id from ingressInsert
    where product_type = 'INGRESS'
),
networkInsert as (
    insert into accounting.accounting_units
    (name, name_plural, floating_point, display_frequency_suffix)
    values ('IP', 'IPs', false, false) returning id
),
update_categories_network as (
    update accounting.product_categories
    set accounting_unit = id from networkInsert
    where product_type = 'NETWORK_IP'
),
storageInsert as (
    insert into accounting.accounting_units
    (name, name_plural, floating_point, display_frequency_suffix)
    values ('Gigabyte', 'Gigabytes', false, false) returning id
),
update_categories_storage as (
    update accounting.product_categories
    set accounting_unit = id from storageInsert
    where product_type = 'STORAGE'
),
coreHourInsert as (
    insert into accounting.accounting_units
    (name, name_plural, floating_point, display_frequency_suffix)
    values ('Core hour', 'Core hours', false, true)
)
update accounting.product_categories
set accounting_unit = id from coreHourInsert
where unit_of_price like 'UNITS_PER%';

alter table accounting.product_categories add foreign key (accounting_unit) references accounting.accounting_units(id);

create or replace function accounting.product_category_to_json(
    pc accounting.product_categories,
    au accounting.accounting_units
) returns jsonb language sql as $$
    select jsonb_build_object(
        'id', pc.id,
        'category', pc.category,
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
    transaction_id text not null references transaction_history(transaction_id),
    description text,
    usage bigint,
    product_id text
);


