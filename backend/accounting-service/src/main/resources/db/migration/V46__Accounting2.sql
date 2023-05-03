create table if not exists accounting.accounting_units (
    id bigint not null unique primary key,
    name text not null,
    name_plural text not null,
    floating_point bool not null,
    display_frequency_suffix bool not null
);

create table if not exists accounting.accounting_unit_conversions (
    id bigint not null unique primary key,
    accounting_unit bigint not null,
    product_category bigint not null,
    factor real,
    constraint products_provider_category_fkey
		foreign key (accounting_unit) references accounting.accounting_units,
		foreign key (product_category) references accounting.product_categories
);

alter table accounting.product_categories add column accounting_unit bigint;

with pcs as (
    select *
    from accounting.product_categories
)


alter table accounting.product_categories add foreign key (accounting_unit) references accounting.accounting_units(id);
