alter table provider.resource add column created_at timestamptz default now() not null;
alter table provider.resource add column created_by text references auth.principals(id);
alter table provider.resource add column project text references project.projects(id) default null;
alter table provider.resource_acl_entry drop column resource_id;
alter table provider.resource drop column id;
alter table provider.resource add column id bigserial primary key;
alter table provider.resource_acl_entry add column resource_id bigint references provider.resource(id);

alter table accounting.products drop constraint if exists products_provider_fkey;
alter table accounting.products drop constraint if exists products_provider_category_fkey;
alter table accounting.wallets drop constraint if exists wallets_product_provider_fkey;
alter table accounting.wallets drop constraint if exists wallets_product_provider_product_category_fkey;
alter table accounting.transactions drop constraint if exists transactions_product_provider_product_category_fkey;
alter table accounting.transactions drop constraint if exists transactions_product_provider_fkey;
alter table accounting.transactions drop constraint if exists transactions_account_id_fkey;
alter table accounting.product_categories add column id bigserial not null;
alter table accounting.product_categories drop constraint if exists product_categories_pkey;
alter table accounting.product_categories add constraint product_categories_uniq unique (provider, category);
alter table accounting.product_categories add primary key (id);

alter table accounting.products add column new_category bigint references accounting.product_categories;
update accounting.products p
set
    new_category = (
        select id
        from accounting.product_categories c
        where c.category = p.category and c.provider = p.provider
    )
where true;
alter table accounting.products drop column provider;
alter table accounting.products drop column category;
alter table accounting.products rename column new_category to category;
alter table accounting.products alter column category set not null;

alter table accounting.transactions drop constraint if exists transactions_account_id_account_type_product_category_prod_fkey;
alter table accounting.wallets add column new_category bigint references accounting.product_categories;
update accounting.wallets w
set
    new_category = (
        select id from accounting.product_categories c
        where c.category = w.product_category and c.provider = w.product_provider
    )
where true;
alter table accounting.wallets rename column new_category to category;
alter table accounting.wallets alter column category set not null;
alter table accounting.wallets drop constraint wallets_pkey;
alter table accounting.wallets add column id bigserial primary key;
create unique index on accounting.wallets (account_id, account_type, category);
alter table accounting.wallets drop column product_provider;
alter table accounting.wallets drop column product_category;

alter table accounting.transactions add column wallet bigint references accounting.wallets(id);
alter table accounting.transactions rename to transactions_backup;
create table accounting.transactions (like accounting.transactions_backup including all);

update accounting.transactions t
set
    wallet = (
        select w.id
        from accounting.wallets w join accounting.product_categories pc on w.category = pc.id
        where
            pc.category = t.product_category and
            pc.provider = t.product_provider and
            w.account_id = t.account_id and
            w.account_type = t.account_type
    )
where true;
alter table accounting.transactions drop column product_provider;
alter table accounting.transactions drop column product_category;
alter table accounting.transactions drop column account_id;
alter table accounting.transactions drop column account_type;
alter table accounting.transactions alter column wallet set not null;

alter table accounting.products add unique (id, category);
alter table accounting.products rename column id to name;
alter table accounting.products add column id bigserial primary key ;
alter table accounting.transactions add column product bigint references accounting.products(id);
update accounting.transactions t
set
    product = (
        select p.id
        from
            accounting.wallets w,
            accounting.products p join accounting.product_categories pc on p.category = pc.id
        where
            p.name = t.product_id and
            w.id = t.wallet and
            pc.id = w.category
    )
where true;
alter table accounting.transactions drop column product_id;

alter table provider.resource add column product bigint references accounting.products(id);
alter table provider.resource add foreign key (provider) references provider.providers(id);
alter table provider.resource alter column provider drop not null;
