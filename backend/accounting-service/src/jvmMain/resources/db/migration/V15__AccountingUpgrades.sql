create extension if not exists ltree;
create type accounting.product_type as enum ('COMPUTE', 'STORAGE', 'INGRESS', 'LICENSE', 'NETWORK_IP');
create type accounting.charge_type as enum ('ABSOLUTE', 'DIFFERENTIAL_QUOTA');
create type accounting.product_price_unit as enum ('PER_MINUTE', 'PER_HOUR', 'PER_DAY', 'PER_WEEK', 'PER_UNIT');
create type accounting.allocation_selector_policy as enum ('ORDERED', 'EXPIRE_FIRST');
create type accounting.transaction_type as enum ('transfer', 'charge', 'deposit', 'allocation_update');
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

alter table accounting.products drop column area;

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

-- TODO(Dan): Insert products and define price_per_unit for DIFFERENTIAL_QUOTA products

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

create unique index wallet_owner_uniq on accounting.wallet_owner (coalesce(username, ''), coalesce(project_id, ''));

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


---- Wallet allocations ----

create table accounting.wallet_allocations(
    id bigserial primary key,
    associated_wallet bigint not null references accounting.wallets(id),
    balance bigint not null,
    initial_balance bigint not null,
    start_date timestamptz not null,
    end_date timestamptz,
    allocation_path ltree not null

    -- NOTE(Dan): we can trace this back to the original transaction by doing a reverse-lookup
);

---- /Wallet allocations ----


---- Additions to transactions ----

create table accounting.new_transactions(
    id bigserial primary key,
    type accounting.transaction_type not null,
    created_at timestamptz not null default now(),
    affected_allocation_id bigint references accounting.wallet_allocations(id) not null,
    action_performed_by text not null references auth.principals(id),
    change bigint not null,
    description varchar(1000) not null,

    source_allocation_id bigint references accounting.wallet_allocations(id) default null,
    product_id bigint references accounting.products(id) default null,
    number_of_products bigint default null,
    units bigint default null,

    start_date timestamptz default null,
    end_date timestamptz default null,

    constraint valid_charge check (
        type != 'charge' or (
            source_allocation_id is not null and
            product_id is not null and
            number_of_products > 0 and
            units > 0 and
            start_date is null and
            end_date is null
        )
    ),

    constraint valid_deposit check (
        type != 'deposit' or (
            -- NOTE(Dan): source_allocation_id almost always needs to be not-null
            product_id is null and
            number_of_products is null and
            units is null and
            start_date is not null
        )
    ),

    constraint valid_transfer check(
        type != 'transfer' or (
            source_allocation_id is not null and
            product_id is null and
            number_of_products is null and
            units is null and
            start_date is not null
        )
    ),

    constraint valid_update check(
        type != 'allocation_update' or (
            source_allocation_id is null and
            product_id is null and
            number_of_products is null and
            units is null and
            start_date is not null
        )
    )
);

alter table accounting.transactions rename to old_transactions;

---- /Additions to transactions ----


---- Transfer all personal workspace balances ----

insert into auth.principals
    (dtype, id, created_at, modified_at, role, first_names, last_name, orc_id, phone_number, title, hashed_password,
     salt, org_id, email)
values
    ('SERVICE', '_ucloud', now(), now(), 'SERVICE', null, null, null, null, null, null, null, null, null)
on conflict do nothing;

with new_allocations as (
    insert into accounting.wallet_allocations
        (id, associated_wallet, balance, initial_balance, start_date, end_date, allocation_path)
        select
            nextval('accounting.wallet_allocations_id_seq'),
            w.id,
            w.balance,
            w.balance,
            now(),
            null,
            currval('accounting.wallet_allocations_id_seq')::text::ltree
        from
            accounting.wallet_owner wo join
            accounting.wallets w on
                        wo.username = w.account_id and
                        w.account_type = 'USER' and
                        wo.username is not null
        returning id, balance
)
insert into accounting.new_transactions
    (type, affected_allocation_id, action_performed_by, change, description, start_date)
select 'deposit', id, '_ucloud', balance, 'Initial balance', now()
from new_allocations;

---- /Transfer all personal workspace balances ----


---- Transfer all project balances ----

with new_allocations as (
    insert into accounting.wallet_allocations
        (id, associated_wallet, balance, initial_balance, start_date, end_date, allocation_path)
    select
        nextval('accounting.wallet_allocations_id_seq'),
        w.id,
        w.balance,
        w.balance,
        now(),
        null,
        currval('accounting.wallet_allocations_id_seq')::text::ltree
    from
        accounting.wallet_owner wo join
        accounting.wallets w on
            wo.username = w.account_id and
            w.account_type = 'PROJECT' and
            wo.project_id is not null
    returning id, balance
)
insert into accounting.new_transactions
(type, affected_allocation_id, action_performed_by, change, description, start_date)
select 'deposit', id, '_ucloud', balance, 'Initial balance', now()
from new_allocations;

-- TODO(Dan): The allocation path is wrong. It needs to match the current project hierarchy, which I don't think we
--   can get without a full traversal of the project hierarchy [O(n) queries likely].

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


---- Verify allocation updates ----

create or replace function accounting.allocation_path_check() returns trigger as $$
declare
    has_violation boolean;
begin
    select bool_or(is_cyclic) into has_violation
    from (
        select allocation_path @> new.allocation_path as is_cyclic
        from accounting.wallet_allocations
        where
            associated_wallet = new.associated_wallet and
            id != new.id
    ) checks;

    if has_violation then
        raise exception 'Update would introduce a cyclic allocation';
    end if;

    return null;
end;
$$ language plpgsql;

create constraint trigger allocation_path_check
after insert or update of allocation_path on accounting.wallet_allocations
for each row execute function accounting.allocation_path_check();

create or replace function accounting.allocation_date_check() returns trigger as $$
declare
    is_valid boolean;
begin
    select bool_or(valid) into is_valid
    from (
        select
            (ancestor.start_date <= new.start_date) and
            (
                ancestor.end_date is null or
                ancestor.end_date >= new.end_date
            ) is true as valid
        from accounting.wallet_allocations ancestor
        where
            ancestor.allocation_path @> new.allocation_path and
            ancestor.id != new.id
    ) checks;

    if not is_valid then
        raise exception 'Update would extend allocation period';
    end if;

    select bool_or(valid) into is_valid
    from (
        select
            (new.start_date <= descendant.start_date) and
            (
                new.end_date is null or
                new.end_date >= descendant.end_date
            ) is true as valid
        from accounting.wallet_allocations descendant
        where
            new.allocation_path @> descendant.allocation_path and
            descendant.id != new.id
    ) checks;

    if not is_valid then
        raise exception 'Update would extend allocation period';
    end if;

    return null;
end;
$$ language plpgsql;

create constraint trigger allocation_date_check
after insert or update of start_date, end_date on accounting.wallet_allocations
for each row execute function accounting.allocation_date_check();


---- /Verify allocation updates ----

---- Product category relationship ----

create table accounting.product_category_relationship(
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


---- Procedures ----

-- NOTE(Dan): We needed to change the structure such that an allocation is a child of an allocation and not a wallet.
-- When an allocation is a child of a wallet it becomes extremely hard to verify that we do not create cycles in
-- the allocation graph. When an allocation is a child of an allocation it becomes trivial, we simply have to check
-- for duplicates in the path.

create type accounting.charge_request as (
    payer text,
    payer_is_project boolean,
    units bigint,
    number_of_products bigint,
    product_name text,
    product_cat_name text,
    product_provider text,
    performed_by text,
    description text
);

create or replace function accounting.process_charge_requests(
    requests accounting.charge_request[]
) returns void language plpgsql as $$
begin
    create temporary table charge_result on commit drop as
        with
            requests as (
                -- NOTE(Dan): DataGrip/IntelliJ thinks these are unresolved. They are not.
                select payer, payer_is_project, units, number_of_products, product_name, product_cat_name,
                       product_provider, performed_by, description,
                       row_number() over () local_request_id
               from unnest(requests) r
            ),
            -- NOTE(Dan): product_and_price finds the product relevant for this request. It is used later to resolve the
            -- correct price and resolve the correct wallets.
            product_and_price as (
                select
                    p.id product_id,
                    request.units * request.number_of_products * p.price_per_unit as payment_required,
                    request.*
                from
                    requests request join
                    accounting.products p on request.product_name = p.name join
                    accounting.product_categories pc on
                        p.category = pc.id and
                        pc.category = request.product_cat_name and
                        pc.provider = request.product_provider
            ),
            -- NOTE(Dan): leaf_charges determines which leaf allocations to charge and how much can be subtracted from
            -- each allocation.
            --
            -- The code below will attempt to charge as much as possible from the allocations until the
            -- target is reached. If all of the allocations combined do not have enough credits then all allocations will
            -- be emptied. That is, we charge as much as possible. This is useful for the common case, which is that we are
            -- charging for resources which have already been consumed.
            --
            -- A leaf allocation is any allocation that:
            --   1) Matches the product
            --   2) Belongs directly to the payer
            leaf_charges as (
                select
                    id,
                    allocation_path,
                    associated_wallet,
                    balance - greatest(0, balance - (payment_required - (balance_available - balance))) as subtracted,
                    product_id,
                    units, number_of_products, performed_by, description,
                    payment_required, local_request_id
                from
                    product_and_price p,
                    lateral (
                        select
                            alloc.id,
                            alloc.balance,
                            alloc.allocation_path,
                            alloc.associated_wallet,
                            -- NOTE(Dan): It is very important that we do not have ambiguity in the sort order as this will cause
                            -- invalid results, hence we sort by the ID when we have identical end dates.
                            sum(alloc.balance) over (order by alloc.end_date nulls last, alloc.id) as balance_available
                        from
                            accounting.wallet_allocations alloc join
                            accounting.wallets w on alloc.associated_wallet = w.id join
                            accounting.wallet_owner wo on w.owned_by = wo.id
                        where
                            (
                                (payer_is_project and wo.project_id = payer) or
                                (not payer_is_project and wo.username = payer)
                            ) and
                            now() >= alloc.start_date and
                            (alloc.end_date is null or now() <= alloc.end_date)
                    ) allocations
                where
                    balance_available - balance < payment_required
            )
    -- NOTE(Dan): Finally, we combine the leaf allocations with ancestor allocations. We will charge as much as
    -- possible, but no more than what we subtracted from the child allocation, in every ancestor.
    --
    -- It is possible that an ancestor allocation does not have enough money.
    -- TODO(Dan): In this case, should we simply place an overcharge on the ancestor?
    select
        leaves.id as leaf_id,
        leaves.associated_wallet as leaf_wallet,
        ancestor_alloc.id as local_id,
        balance - greatest(0, balance - subtracted) as local_subtraction,
        ancestor_alloc.allocation_path,
        ancestor_alloc.associated_wallet as local_wallet,
        product_id,
        units, number_of_products, performed_by, description,
        payment_required, local_request_id
    from
        leaf_charges leaves join
        accounting.wallet_allocations ancestor_alloc on leaves.allocation_path <@ ancestor_alloc.allocation_path;
end;
$$;

-- noinspection SqlResolve
create or replace function accounting.charge(
    requests accounting.charge_request[]
) returns void language plpgsql as $$
begin
    -- TODO(Dan): This is currently lacking replay protection
    perform accounting.process_charge_requests(requests);

    -- NOTE(Dan): Update the balance of every relevant allocation
    update accounting.wallet_allocations alloc
    set balance = balance - res.local_subtraction
    from charge_result res
    where alloc.id = res.local_id;

    -- NOTE(Dan): Insert a record of every change we did in the transactions table
    insert into accounting.transactions
            (type, created_at, affected_allocation_id, action_performed_by, change, description,
             source_allocation_id, product_id, number_of_products, units)
    select 'charge', now(), res.local_id, res.performed_by, res.local_subtraction, res.description,
           res.leaf_id, res.product_id, res.number_of_products, res.units
    from charge_result res;
end;
$$;

-- noinspection SqlResolve
create or replace function accounting.credit_check(
    requests accounting.charge_request[]
) returns boolean language plpgsql as $$
begin
    perform accounting.process_charge_requests(requests);
    return (
        select bool_and(has_enough_credits)
        from (
            select sum(group_subtraction)::bigint = payment_required has_enough_credits
            from (
                select min(local_subtraction) group_subtraction, payment_required, local_request_id
                from charge_result
                group by leaf_id, payment_required, local_request_id
            ) min_per_group
            group by local_request_id, payment_required
        ) credit_check
    );
end;
$$;

create type accounting.deposit_request as (
    initiated_by text,
    recipient text,
    recipient_is_project boolean,
    source_allocation bigint,
    desired_balance bigint,
    start_date timestamptz,
    end_date timestamptz,
    description text
);

create or replace function accounting.deposit(
    request accounting.deposit_request
) returns void language plpgsql as $$
begin
    create temporary table deposit_result on commit drop as
        -- NOTE(Dan): Resolve and verify source wallet, potentially resolve destination wallet
        select
            -- NOTE(Dan): we pre-allocate the IDs to make it easier to connect the data later
            nextval('accounting.wallet_allocations_id_seq') idx,
            source_wallet.id source_wallet,
            source_wallet.category product_category,
            source_alloc.allocation_path source_allocation_path,
            target_wallet.id target_wallet,
            request.recipient,
            request.recipient_is_project,
            coalesce(request.start_date, now()),
            request.end_date,
            request.desired_balance,
            request.initiated_by,
            source_wallet.category
        from
            accounting.wallet_allocations source_alloc join
            accounting.wallets source_wallet on source_alloc.associated_wallet = source_wallet.id join
            accounting.wallet_owner source_owner on source_wallet.owned_by = source_owner.id left join
            project.project_members pm on source_owner.project_id = pm.project_id and pm.role = 'ADMIN' left join

            accounting.wallet_owner target_owner on
                (request.recipient_is_project and target_owner.project_id = request.recipient) or
                (not request.recipient_is_project and target_owner.username = request.recipient) left join
            accounting.wallets target_wallet on
                target_wallet.owned_by = target_owner and
                target_wallet.category = source_wallet.category
        where
            request.initiated_by = source_owner.username or
            request.initiated_by = pm.username;

    -- NOTE(Dan): We don't know for sure that the wallet_owner doesn't exist, but it might not exist since there is
    -- no wallet.
    insert into accounting.wallet_owner (username, project_id)
    select
        case r.recipient_is_project when false then r.recipient end,
        case r.recipient_is_project when true then r.recipient end
    from deposit_result r
    where target_wallet is null
    on conflict do nothing;

    -- NOTE(Dan): Create the missing wallets.
    insert into accounting.wallets (category, owned_by)
    select r.category, wo.id
    from
        deposit_result r join
        accounting.wallet_owner wo on
            (r.recipient_is_project and wo.project_id = r.recipient) or
            (not r.recipient_is_project and wo.username = r.recipient)
    where target_wallet is null;

    -- NOTE(Dan): Update the result such that all target_wallets are not null
    update deposit_result r
    set target_wallet = w.id
    from
        accounting.wallet_owner wo join
        accounting.wallets w on wo.id = w.owned_by
    where
        (r.recipient_is_project and wo.project_id = r.recipient) or
        (not r.recipient_is_project and wo.username = r.recipient);

    -- NOTE(Dan): Create allocations and insert transactions
    with new_allocations as (
        insert into accounting.wallet_allocations
            (id, associated_wallet, balance, initial_balance, start_date, end_date,
             allocation_path)
        select
            r.idx,
            r.target_wallet,
            r.desired_balance,
            r.desired_balance,
            r.start_date,
            r.end_date,
            (r.source_allocation_path::text || '.' || r.idx::text)::ltree
        from deposit_result r
        where r.target_wallet is not null
        returning id, balance
    )
    insert into accounting.new_transactions
    (type, affected_allocation_id, action_performed_by, change, description, start_date)
    select 'deposit', alloc.id, r.initiated_by, alloc.balance, 'Initial balance', now()
    from
        new_allocations alloc join
        deposit_result r on alloc.id = r.idx;
end;
$$;

---- /Procedures ----
