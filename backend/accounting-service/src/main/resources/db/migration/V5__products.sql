drop table if exists machines;

create table product_categories(
    provider text not null,
    category text not null,
    area text not null,
    primary key (provider, category, area)
);

create unique index product_categories_id on product_categories (upper(provider), upper(category));

create table products(
    provider text not null,
    category text not null,
    area text not null,
    id text not null,
    price_per_unit bigint not null,
    description text not null default '',
    availability text default null,
    priority int default 0,
    cpu int default null,
    gpu int default null,
    memory_in_gigs int default null,

    primary key (id, provider, category),
    foreign key (provider, category, area) references product_categories(provider, category, area)
);

create unique index products_id on products (upper(id), upper(provider), upper(category), upper(area));
