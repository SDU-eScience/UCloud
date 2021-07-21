create type accounting.product_type as enum ('COMPUTE', 'STORAGE', 'INGRESS', 'LICENSE', 'NETWORK_IP');
create type accounting.charge_type as enum ('ABSOLUTE', 'DIFFERENTIAL_QUOTA');
create type accounting.product_price_unit as enum ('PER_MINUTE', 'PER_HOUR', 'PER_DAY', 'PER_WEEK', 'PER_UNIT');
create type accounting.allocation_selector_policy as enum ('ORDERED', 'EXPIRE_FIRST');
create type accounting.transaction_type as enum ('TRANSFER', 'CHARGE', 'DEPOSIT');
create type accounting.product_category_relationship_type as enum ('STORAGE_CREDITS', 'NODE_HOURS');

---- Changes to product_categories ----

alter table accounting.product_categories add column product_type accounting.product_type;
update accounting.product_categories c
set product_type = c.area::accounting.product_type
where true;
alter table accounting.product_categories alter column product_type set not null;

alter table accounting.product_categories add column charge_type accounting.charge_type not null default 'ABSOLUTE';

alter table accounting.product_categories add foreign key (provider) references provider.providers(unique_name);

alter table accounting.product_categories drop column area;

---- /Changes to product_categories ----


---- Update storage products ----

-- This migration splits all storage products into two variants. The original product becomes the credits variant and
-- a copy is created for quotas.

update accounting.product_categories
set category = category || '_credits'
where product_type = 'STORAGE';

with storage_products as (
    select *
    from accounting.product_categories
    where product_categories.product_type = 'STORAGE'
)
insert into accounting.product_categories (provider, category, product_type, charge_type)
select provider, replace(category, '_credits', '_quota'), product_type, 'DIFFERENTIAL_QUOTA'
from storage_products;

---- /Update storage products ----


---- Add new columns to products ----

alter table accounting.products add column unit_of_price accounting.product_price_unit;
update accounting.products p
set unit_of_price = case pc.product_type
    when 'COMPUTE' then 'PER_MINUTE'::accounting.product_price_unit
    when 'STORAGE' then 'PER_DAY'::accounting.product_price_unit
    else 'PER_UNIT'::accounting.product_price_unit
end
from accounting.product_categories pc
where p.category = pc.id;

alter table accounting.products add column version bigint not null default 1;

alter table accounting.products add column free_to_use boolean;
update accounting.products p
set
    free_to_use = case p.payment_model
        when 'FREE_BUT_REQUIRE_BALANCE' then true
        else false
    end
where true;
alter table accounting.products alter column free_to_use set not null;

alter table accounting.products drop column payment_model;

---- /Add new columns to products ----


---- Wallet owners ----

create table accounting.wallet_owner(
    id bigserial primary key,
    username text references auth.principals(id),
    project_id text references project.projects(id)
    constraint check_only_one_owner check (
         (username is not null and project_id is null) or
         (username is null and project_id is not null)
    )
);

insert into accounting.wallet_owner (username)
select id from auth.principals
where role = 'USER' or role = 'ADMIN';

insert into accounting.wallet_owner (project_id)
select id from project.projects;

---- /Wallet owners ----


---- Allocation selector policy ----

alter table accounting.wallets add column allocation_selector_policy accounting.allocation_selector_policy not null
    default 'EXPIRE_FIRST';

---- /Allocation selector policy ----


---- Additions to transactions ----

create table accounting.new_transactions(
    id bigserial primary key,
    transaction_type accounting.transaction_type not null,
    target_wallet_id bigint not null references accounting.wallets(id),
    units bigint not null,
    number_of_products bigint not null,
    action_performed_by text references auth.principals(id),
    action_performed_by_wallet bigint references accounting.wallets(id),
    product_id bigint references accounting.products(id),
    transfer_from_wallet_id bigint references accounting.wallets(id),
    description text not null,
    created_at timestamptz not null default now(),
    -- Change in wallet_allocation: product.price_per_unit * units * number_of_products
    constraint check_deposit_convention check (
        transaction_type != 'DEPOSIT' or number_of_products = 1
    ),
    constraint check_target check(
        transaction_type != 'TRANSFER' or transfer_from_wallet_id is not null
    )

    -- TODO enforce that product id matches wallet (also check on backend)
);

alter table accounting.transactions rename to old_transactions;

---- /Additions to transactions ----


---- Wallet allocations ----

create table accounting.wallet_allocations(
    id bigserial primary key,
    associated_wallet bigint not null references accounting.wallets(id),
    balance bigint not null,
    initial_balance bigint not null,
    start_date timestamptz not null,
    end_date timestamptz,
    parent_wallet_id bigint references accounting.wallets(id),

    -- TODO Check that this is charge or deposit (seems to must be done in backend)
    transaction_id bigint not null references accounting.new_transactions(id)
);

---- /Wallet allocations ----


---- Transfer all personal workspace balances ----

insert into accounting.new_transactions
    (transaction_type, target_wallet_id, units, number_of_products, action_performed_by, action_performed_by_wallet,
     product_id, transfer_from_wallet_id, description)
select
    'DEPOSIT',
    (result.w).id,
    ceil((result.w).balance / greatest(1, (result.p).price_per_unit)),
    1,
    null,
    null,
    (result.p).id,
    null,
    'Initial balance'
from
    (
        select row_number() over (partition by w.id order by p.price_per_unit) row_number, w, p
        from
            accounting.wallet_owner wo join
            accounting.wallets w on
                wo.username = w.account_id and
                w.account_type = 'USER' and
                wo.username is not null join
            accounting.products p on p.category = w.category
    ) result
where
    result.row_number <= 1;

insert into accounting.wallet_allocations
    (associated_wallet, balance, initial_balance, start_date, end_date, parent_wallet_id, transaction_id)
select
    nt.target_wallet_id,
    greatest(1::bigint, nt.units * p.price_per_unit),
    greatest(1::bigint, nt.units * p.price_per_unit),
    now(),
    null,
    null,
    nt.id
from
    accounting.new_transactions nt join
    accounting.products p on nt.product_id = p.id
where
    transaction_type = 'DEPOSIT';

---- /Transfer all personal workspace balances ----


---- Transfer all project balances ----

insert into accounting.new_transactions
    (transaction_type, target_wallet_id, units, number_of_products, action_performed_by, action_performed_by_wallet,
     product_id, transfer_from_wallet_id, description)
select
    'DEPOSIT',
    (result.w).id,
    ceil((result.w).balance / greatest(1, (result.prod).price_per_unit)),
    1,
    null,
    (result.parent_w).id,
    (result.prod).id,
    null,
    'Initial balance'
from
    (
        select row_number() over (partition by w.id order by prod.price_per_unit) row_number, w, prod, parent_w
        from
            accounting.wallet_owner wo join
            accounting.wallets w on
                wo.project_id = w.account_id and
                w.account_type = 'PROJECT' and
                wo.project_id is not null join
            accounting.products prod on prod.category = w.category join
            project.projects project on wo.project_id = project.id left join
            accounting.wallets parent_w
                on project.parent = parent_w.account_id and parent_w.account_type = 'PROJECT'
    ) result
where
    result.row_number <= 1;

insert into accounting.wallet_allocations
    (associated_wallet, balance, initial_balance, start_date, end_date, parent_wallet_id, transaction_id)
select
    nt.target_wallet_id,
    greatest(1::bigint, nt.units * p.price_per_unit),
    greatest(1::bigint, nt.units * p.price_per_unit),
    now(),
    null,
    nt.transfer_from_wallet_id,
    nt.id
from
    accounting.new_transactions nt join
    accounting.products p on nt.product_id = p.id join
    accounting.wallet_owner wo on nt.target_wallet_id = wo.id and wo.project_id is not null
where
    transaction_type = 'DEPOSIT';

---- /Transfer all project balances ----


---- Add wallet owners to wallets ----

alter table accounting.wallets add column owned_by bigint references accounting.wallet_owner;
update accounting.wallets w
set owned_by = wo.id
from accounting.wallet_owner wo
where
    (w.account_type = 'PROJECT' and wo.project_id = w.account_id) or
    (w.account_type = 'USER' and wo.username = w.account_id);
alter table accounting.wallets drop column account_id;
alter table accounting.wallets drop column account_type;
alter table accounting.wallets alter column owned_by set not null;

---- /Add wallet owners to wallets ----


---- Product category relationship ----

create table product_category_relationship(
    type accounting.product_category_relationship_type not null,
    credits_category bigint references accounting.product_categories(id),
    quota_category bigint references accounting.product_categories(id),
    hours_category bigint references accounting.product_categories(id),
    constraint storage_credits check(
              type != 'STORAGE_CREDITS' or (
              credits_category is not null and
              quota_category is not null
          )
      ),
    constraint node_hours check(
          type != 'NODE_HOURS' or
          (
              credits_category is not null and
              hours_category is not null
          )
      )
);

---- /Product category relationship ----


---- Rename transactions table ----

alter table accounting.new_transactions rename to transactions;

---- /Rename transactions table ----


---- Remove unused properties from wallets ----

drop function accounting.initialize_allocated() cascade;
drop function accounting.update_allocated() cascade;
alter table accounting.wallets drop column balance;
alter table accounting.wallets drop column allocated;
alter table accounting.wallets drop column used;

---- /Remove unused properties from wallets ----


---- Remove legacy tables ----

drop table accounting.job_completed_events;
drop table accounting.old_transactions;

---- /Remove legacy tables ----
