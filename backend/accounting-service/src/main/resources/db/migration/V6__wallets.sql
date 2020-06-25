drop table if exists transactions;
drop table if exists grant_administrators;
drop table if exists balance;

alter table products drop constraint if exists products_provider_fkey;

alter table product_categories drop constraint if exists product_categories_pkey cascade;
alter table product_categories add primary key (provider, category);

alter table products add foreign key (provider, category) references product_categories(provider, category);

create table wallets
(
    account_id       text   not null,
    account_type     text   not null,
    product_category text   not null,
    product_provider text   not null,
    balance          bigint not null default 0,
    primary key (account_id, account_type, product_category, product_provider),
    foreign key (product_provider, product_category) references product_categories (provider, category)
);

create table transactions
(
    account_id          text      not null,
    account_type        text      not null,
    product_category    text      not null,
    product_provider    text      not null,
    id                  text      not null,
    product_id          text      not null,
    units               bigint    not null,
    amount              bigint    not null,
    is_reserved         bool default false,
    initiated_by        text      not null,
    completed_at        timestamp not null,
    original_account_id text      not null,
    expires_at          timestamp,
    primary key (id, account_id, account_type),
    foreign key (product_provider, product_category) references product_categories (provider, category),
    foreign key (account_id, account_type, product_category, product_provider) references wallets (account_id, account_type, product_category, product_provider)
);
