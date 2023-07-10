drop extension if exists "uuid-ossp";
create extension "uuid-ossp" schema public;
create extension if not exists ltree schema public;
create type accounting.product_type as enum ('COMPUTE', 'STORAGE', 'INGRESS', 'LICENSE', 'NETWORK_IP');
create type accounting.charge_type as enum ('ABSOLUTE', 'DIFFERENTIAL_QUOTA');
create type accounting.product_price_unit as enum (
    'PER_UNIT',
    'CREDITS_PER_MINUTE', 'CREDITS_PER_HOUR', 'CREDITS_PER_DAY',
    'UNITS_PER_MINUTE', 'UNITS_PER_HOUR', 'UNITS_PER_DAY'
);
create type accounting.allocation_selector_policy as enum ('EXPIRE_FIRST');
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

alter table accounting.products drop column availability;

alter table accounting.product_categories add column unit_of_price accounting.product_price_unit;
update accounting.product_categories pc
set unit_of_price = case pc.product_type
    when 'COMPUTE' then 'CREDITS_PER_MINUTE'::accounting.product_price_unit
    when 'STORAGE' then 'CREDITS_PER_DAY'::accounting.product_price_unit
    else 'PER_UNIT'::accounting.product_price_unit
end
where true;

alter table accounting.products add column free_to_use boolean;
update accounting.products p
set
    free_to_use = case
        when p.price_per_unit = 0 and p.payment_model = 'FREE_BUT_REQUIRE_BALANCE' then false
        when p.price_per_unit = 0 then true
        else false
    end
where true;

with per_unit_products as (
    select products.id product_id, product_categories.unit_of_price unit
    from
    accounting.products  join
    accounting.product_categories  on
        products.category = product_categories.id
)
UPDATE accounting.products
SET price_per_unit = 1
from per_unit_products
WHERE id = per_unit_products.product_id and
        per_unit_products.unit = 'PER_UNIT'::accounting.product_price_unit;

update accounting.products
set description = name
where description = '';

create or replace function accounting.require_product_description() returns trigger language plpgsql as $$
begin
    if (new.description = '' or new.description is null) then
        raise exception 'description cannot be empty or null';
    end if;
    return null;
end;
$$;

create trigger require_product_description
after update or insert on accounting.products
for each row execute procedure accounting.require_product_description();

create or replace function accounting.require_immutable_product_category() returns trigger language plpgsql as $$
begin
    if old.charge_type != new.charge_type or old.product_type != new.product_type or old.unit_of_price != new.unit_of_price then
        raise exception 'Cannot change the definition of a category after its initial creation';
    end if;
    return null;
end;
$$;

create trigger require_immutable_product_category
after update of charge_type, product_type on accounting.product_categories
for each row execute procedure accounting.require_immutable_product_category();

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

    if (current_charge_type = 'DIFFERENTIAL_QUOTA' and new.price_per_unit != 1) then
        raise exception 'Price per unit for differential_quota products can only be 1';
    end if;
    return null;
end;
$$;


create trigger require_fixed_price_per_unit_for_diff
after insert or update on accounting.products
for each row execute procedure accounting.require_fixed_price_per_unit_for_diff_quota();

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
        new.price_per_unit != 1) then
        raise exception 'Price per unit for UNITS_PER_X or PER_UNIT products can only be 1';
    end if;
    return null;
end;
$$;

create trigger require_fixed_price_per_unit_for_units
after insert or update on accounting.products
for each row execute procedure accounting.require_fixed_price_per_unit_for_unit_per_x();

create or replace function accounting.require_fixed_price_per_unit_for_free_to_use() returns trigger language plpgsql as $$
begin

    if (new.free_to_use and new.price_per_unit != 1) then
        raise exception 'Price per unit for free_to_use products can only be 1';
    end if;
    return null;
end;
$$;

create trigger require_fixed_price_per_unit_for_free
after insert or update on accounting.products
for each row execute procedure accounting.require_fixed_price_per_unit_for_free_to_use();

---- /Changes to product_categories ----

---- Add new columns to products ----

alter table accounting.products add column version bigint not null default 1;

alter table accounting.products alter column free_to_use set not null;

alter table accounting.products drop column payment_model;

alter table accounting.products drop constraint products_id_category_key;
create unique index if not exists products_id
	on accounting.products (name, category, version);

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
where role = 'USER' or role = 'ADMIN'; -- TODO dtype?

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
    allocation_path ltree not null,
    associated_wallet bigint not null references accounting.wallets(id),

    balance bigint not null,
    initial_balance bigint not null,
    local_balance bigint not null,

    start_date timestamptz not null,
    end_date timestamptz,

    granted_in bigint references "grant".applications(id) default null

    -- NOTE(Dan): we can trace this back to the original transaction by doing a reverse-lookup
);

comment on column accounting.wallet_allocations.id is $$
The primary key of wallet allocations, used mostly for referencing. The id is always embedded as the last component of
allocation_path.
$$;

comment on column accounting.wallet_allocations.allocation_path is $$
The allocation path creates a hierarchy of allocations. Many columns and operations have invariants which depend on
this hierarchy. Each component of the path contains a reference to an allocation. The last component is always a
reference to this allocation. The last component contain this reference to make many ancestor/descendant queries easier
to use.
$$;

comment on column accounting.wallet_allocations.associated_wallet is $$
A reference to the wallet which owns this allocation. A wallet may have 0 or more allocations active allocations at
any point (see start/end_date).
$$;

comment on column accounting.wallet_allocations.balance is $$
The current balance remaining for the sub-tree rooted at this allocation. Note: The sub-tree can be found by querying
for all descendants of allocation_path.

If the balance ever reaches a value of zero or below, then the entire sub-tree of allocations will be locked. A locked
wallet allocation cannot be used in charges.

A balance column can typically become less than zero. This is typically a result of the imprecision involved in the
accounting process. For example, if a provider charges for compute every 15 minutes and a job has been started with only
1 minute of compute time left, then we will likely see a negative balance of 14 minutes. This is not necessarily a sign
of abuse, and allocators should generally forgive small amounts of 'debt' that is caused by this imprecision.

The balance columns is closely associated with the local_balance and max_balance columns. The system has an invariant
that balance <= local_balance <= max_balance. Any change made to the local_balance, must be equally reflected in the
balance of all ancestors. Similarly, any change made to a balance column must be reflected in all ancestor balances.

The unit of the balance column is determined by the product category which the wallet pays for.
$$;

comment on column accounting.wallet_allocations.initial_balance is $$
Contains the maximum value that balance may ever have. There is a strict check that balance <= max_balance. This value
can be used for different use-cases:

1. Can be used to track how much of the allocation has been used, by comparing balance with max_balance.
2. Can be used to track a project's own usage, not counting descendants, by comparing local_balance with max_balance.
$$;

comment on column accounting.wallet_allocations.local_balance is $$
The current balance, for this node in the allocation hierarchy. For hierarchies which have no descendants, this value
value will always be equal to balance.
$$;

comment on column accounting.wallet_allocations.start_date is $$
Timestamp for when this allocation becomes active. The following constraint is enforced:
end_date is null or start_date <= end_date.
$$;

comment on column accounting.wallet_allocations.end_date is $$
Timestamp for when this allocation becomes invalid. This value can be null, which indicates that the allocation will
never expire. The following constraint is enforced: end_date is null or start_date <= end_date.
$$;


comment on column accounting.wallet_allocations.granted_in is $$
References the application grant that is responsible for creating the allocation. This value can be null in the case that
no application was made and that the resource was given another way (e.g. root-deposit).
$$;

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

    transaction_id text not null,
    initial_transaction_id text not null,


    constraint valid_charge check (
        type != 'charge' or (
            source_allocation_id is not null and
            product_id is not null and
            number_of_products > 0 and
            units >= 0 and
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
    (dtype, id, created_at, modified_at, role, first_names, last_name, hashed_password,
     salt, org_id, email)
values
    ('SERVICE', '_ucloud', now(), now(), 'SERVICE', null, null, null, null, null, null)
on conflict do nothing;

with new_allocations as (
    insert into accounting.wallet_allocations
        (id, associated_wallet, balance, initial_balance, local_balance, start_date, end_date,
        allocation_path)
    select
        nextval('accounting.wallet_allocations_id_seq'),
        w.id,
        w.balance,
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
    (type, affected_allocation_id, action_performed_by, change, description, start_date, transaction_id, initial_transaction_id)
select 'deposit', id, '_ucloud', balance, 'Initial balance', now(), uuid_generate_v4(), uuid_generate_v4()
from new_allocations;

---- /Transfer all personal workspace balances ----


---- Transfer all project balances ----

with new_allocations as (
    insert into accounting.wallet_allocations
        (id, associated_wallet, balance, initial_balance, local_balance, start_date, end_date,
        allocation_path)
    select
        nextval('accounting.wallet_allocations_id_seq'),
        w.id,
        w.balance,
        w.balance,
        w.balance,
        now(),
        null,
        currval('accounting.wallet_allocations_id_seq')::text::ltree
    from
        accounting.wallet_owner wo join
        accounting.wallets w on
            wo.project_id = w.account_id and
            w.account_type = 'PROJECT'
    returning id, balance
)
insert into accounting.new_transactions
(type, affected_allocation_id, action_performed_by, change, description, start_date, transaction_id, initial_transaction_id)
select 'deposit', id, '_ucloud', balance, 'Initial balance', now(), uuid_generate_v4(), uuid_generate_v4()
from new_allocations;

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
create unique index on accounting.wallets (owned_by, category);


-- reestablish project hierarchy in allocation paths
create temporary table project_allocation_paths on commit drop as
    with recursive project_tree (project_id, parent_id, title, path, allocation_path, product_cat, wallet_allocation) as (
        select p1.id, p1.parent, p1.title, '' || p1.id, '' || wa.id, w.category, wa.id
        from project.projects p1 join
            accounting.wallet_owner wo on p1.id = wo.project_id join
            accounting.wallets w on wo.id = w.owned_by join
            accounting.wallet_allocations wa on wa.associated_wallet = w.id
        where parent is null

        union all

        select p.id, tree.parent_id, p.title, tree.path || '/' || p.id, tree.allocation_path || '.' || wa.id, w.category, wa.id
            from project.projects p join
            project_tree tree on tree.project_id = p.parent join
            accounting.wallet_owner wo on p.id = wo.project_id join
            accounting.wallets w on wo.id = w.owned_by and w.category = tree.product_cat join
            accounting.wallet_allocations wa on wa.associated_wallet = w.id
        where parent = tree.project_id
    )
    select project_id, text2ltree(allocation_path) allocation_path, product_cat, wallet_allocation
    from project_tree;

update accounting.wallet_allocations
set allocation_path = new_allo.allocation_path
from project_allocation_paths new_allo
where id = new_allo.wallet_allocation;

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
for each row execute procedure accounting.allocation_path_check();

create or replace function accounting.allocation_date_check() returns trigger as $$
declare
    is_valid boolean;
begin
    select bool_or(valid) into is_valid
    from (
        select
            (ancestor.start_date <= updated.start_date) and
            (
                ancestor.end_date is null or
                ancestor.end_date >= updated.end_date
            ) is true as valid
        from
            new_table updated join
            accounting.wallet_allocations ancestor on
                ancestor.allocation_path @> updated.allocation_path and
                ancestor.id != updated.id
    ) checks;

    if not is_valid then
        raise exception 'Update would extend allocation period (caused by ancestor constraint)';
    end if;

    select bool_or(valid) into is_valid
    from (
        select
            (updated.start_date <= coalesce(updated_descendant.start_date, descendant.start_date)) and
            (
                updated.end_date is null or
                updated.end_date >= coalesce(updated_descendant.end_date, descendant.end_date)
            ) is true as valid
        from
            new_table updated join
            accounting.wallet_allocations descendant on
                updated.allocation_path @> descendant.allocation_path and
                descendant.id != updated.id left join
            new_table updated_descendant on descendant.id = updated_descendant.id
    ) checks;


    if not is_valid then
        raise exception 'Update would extend allocation period (caused by descendant constraint)';
    end if;

    return null;
end;
$$ language plpgsql;

create trigger allocation_date_check_insert
after insert on accounting.wallet_allocations
referencing new table as new_table
for each statement execute procedure accounting.allocation_date_check();

create trigger allocation_date_check_update
after update on accounting.wallet_allocations
referencing new table as new_table
for each statement execute procedure accounting.allocation_date_check();


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

---- Deposit notifications ----
create table accounting.deposit_notifications(
    id bigserial primary key,
    created_at timestamptz default now(),
    username text references auth.principals(id),
    project_id text references project.projects(id),
    category_id bigint not null references accounting.product_categories(id),
    balance bigint not null
);
---- /Deposit notifications ----

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
    description text,
    transaction_id text
);

create or replace function accounting.process_charge_requests(
    requests accounting.charge_request[]
) returns void language plpgsql as $$
declare
    charge_count bigint;
    invalid_transactions bigint;
begin
    create temporary table duplicate_transactions on commit drop as
        with requests as (
                select transaction_id,
                       row_number() over () local_request_id
                from unnest(requests) r
            )
        select req.local_request_id, req.transaction_id
        from requests req join accounting.transactions tr on req.transaction_id = tr.transaction_id;

    select count(distinct local_request_id) into invalid_transactions from duplicate_transactions;

    create temporary table product_and_price on commit drop as
        with requests as (
            -- NOTE(Dan): DataGrip/IntelliJ thinks these are unresolved. They are not.
            select payer, payer_is_project, units, number_of_products, product_name, product_cat_name,
                   product_provider, performed_by, description, transaction_id,
                   row_number() over () local_request_id
           from unnest(requests) r
        )
        -- NOTE(Dan): product_and_price finds the product relevant for this request. It is used later to resolve the
        -- correct price and resolve the correct wallets.
        select
            p.id product_id,
            pc.id product_category,
            pc.charge_type,
            request.units * request.number_of_products * p.price_per_unit as payment_required,
            request.*,
            p.free_to_use
        from
            requests request join
            accounting.products p on request.product_name = p.name join
            accounting.product_categories pc on
                p.category = pc.id and
                pc.category = request.product_cat_name and
                pc.provider = request.product_provider left join
            duplicate_transactions dt on dt.local_request_id = request.local_request_id

        where
            p.version = (
                select max(version)
                from accounting.products p2
                where
                    p2.name = p.name and
                    p2.category = p.category
            ) and
            -- Note(Henrik) The join and null search should filter out the duplicates
            dt.local_request_id is null;

    -- If a request has active allocations with credit remaining, then these must be used
    -- However, if a request only has active allocations with no credit, then one must be picked.
    -- We create a single table of all relevant product_and_price + allocations. Later, we will remove allocations
    -- with a negative balance, but only if we have any allocation with balance remaining.

    create temporary table absolute_allocations on commit drop as
        select *
        from
            product_and_price p left join lateral (
                select
                    alloc.id,
                    alloc.balance,
                    alloc.allocation_path,
                    alloc.associated_wallet,
                    alloc.end_date
                from
                    accounting.wallet_allocations alloc join
                    accounting.wallets w on
                        alloc.associated_wallet = w.id and
                        w.category = p.product_category join
                    accounting.wallet_owner wo on w.owned_by = wo.id
                where
                    p.charge_type = 'ABSOLUTE' and
                    (
                        (payer_is_project and wo.project_id = payer) or
                        (not payer_is_project and wo.username = payer) or
                        (p.free_to_use = true)
                    ) and
                    now() >= alloc.start_date and
                    (alloc.end_date is null or now() <= alloc.end_date)
            ) allocations on true;

    delete from absolute_allocations alloc
        where
            (free_to_use != true and balance is null) or
            (
                free_to_use != true and
                balance <= 0 and
                local_request_id in (
                    select alloc.local_request_id from absolute_allocations where balance > 0
                )
            );

        create temporary table absolute_leaves on commit drop as
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
        select
            id,
            allocation_path,
            associated_wallet,
            greatest(0, balance - greatest(0, balance - (payment_required - (balance_available - balance)))) as subtracted,
            product_id,
            units, number_of_products, performed_by, description,
            payment_required, local_request_id,
            balance_available,
            free_to_use,
            transaction_id
        from
            (
                select
                    product_id, units, number_of_products, performed_by, description, payment_required, local_request_id, transaction_id,
                    alloc.id,
                    alloc.balance,
                    alloc.allocation_path,
                    alloc.associated_wallet,
                    alloc.free_to_use,
                    -- NOTE(Dan): It is very important that we do not have ambiguity in the sort order as this will
                    -- cause invalid results, hence we sort by the ID when we have identical end dates.
                    sum(greatest(0, alloc.balance)) over (partition by local_request_id order by alloc.end_date nulls last, alloc.id) as balance_available
                from absolute_allocations alloc
                where charge_type = 'ABSOLUTE'
            ) t
        where
            (balance_available - balance < payment_required) or payment_required = 0 or free_to_use = true;

    create temporary table differential_leaves on commit drop as
        with
            -- NOTE(Dan): Similar to absolute products, we first select the allocations which should be used.
            -- Very importantly, we sum over the initial_balance instead of the actual balance!
            allocation_selection as (
                select *
                from
                    product_and_price p,
                    lateral (
                        select
                            distinct (p.product_id) distinctProductId,
                            alloc.id,
                            alloc.balance,
                            alloc.initial_balance,
                            alloc.local_balance,
                            alloc.allocation_path,
                            alloc.associated_wallet,
                            -- NOTE(Dan): Notice the use of initial_balance!
                            sum(alloc.initial_balance) over (order by alloc.end_date nulls last, alloc.id) as balance_available
                        from
                            accounting.wallet_allocations alloc join
                            accounting.wallets w on
                                alloc.associated_wallet = w.id and
                                w.category = p.product_category join
                            accounting.wallet_owner wo on w.owned_by = wo.id right join
                            accounting.products p2 on p.product_id = p2.id
                        where
                            (

                                (payer_is_project and wo.project_id = payer) or
                                (not payer_is_project and wo.username = payer) or
                                (p.free_to_use = true)
                            ) and
                            (now() >= alloc.start_date or p.free_to_use = true) and
                            (alloc.end_date is null or now() <= alloc.end_date)
                    ) allocations
                where
                    p.charge_type = 'DIFFERENTIAL_QUOTA'
            ),
            -- NOTE(Dan): For differential products we must consider all allocations that a wallet has. If an allocation
            -- is needed then we should subtract from it. If an allocation is not needed to meet the payment_required
            -- then we must add to it (since we are no longer using this allocation). We first find the once which are
            -- needed, this is very similar to how we do it for ABSOLUTE products.
            subtractions as (
                select
                    id,
                    allocation_path,
                    associated_wallet,
                    -- max_requested_from_alloc = balance_available - initial_balance
                    -- to_be_used_here = payment_required - max_requested_from_alloc
                    local_balance - greatest(0::bigint, initial_balance - (payment_required - (balance_available - initial_balance))) as subtracted,
                    product_id,
                    units, number_of_products, performed_by, description,
                    payment_required, local_request_id,
                    balance_available, free_to_use, transaction_id
                from
                    allocation_selection
                where
                    balance_available - initial_balance < payment_required or payment_required = 0 or free_to_use = true
            ),
            -- NOTE(Dan): see note above for explanation about additions CTE.
            additions as (
                select
                    id,
                    allocation_path,
                    associated_wallet,
                    local_balance - initial_balance as change,
                    product_id,
                    units, number_of_products, performed_by, description,
                    payment_required, local_request_id,
                    balance_available, free_to_use, transaction_id
                from
                    allocation_selection
                where
                    balance_available - initial_balance >= payment_required or payment_required = 0 or free_to_use = true
            )
            -- NOTE(Dan): Select all rows from subtractions and additions combined.
            select * from subtractions
            union
            select * from additions;

        create temporary table all_leaves on commit drop as
            select * from absolute_leaves
            union
            select * from differential_leaves;

    update all_leaves leaves
        set subtracted = leaves.subtracted + greatest(0, l.payment_required - missing_payment.max_balance)
        from
            (
                select a.*, row_number() over (partition by local_request_id) rn
                from all_leaves a
            ) l join
            (
                -- NOTE(Dan): Recall that balance_available is the rolling sum over multiple allocations. This is why we
                -- are interested in the max, to determine how much has actually been charged.
                select local_request_id, max(balance_available) as max_balance
                from all_leaves
                group by local_request_id
            ) missing_payment on l.local_request_id = missing_payment.local_request_id
        where
            l.id = leaves.id and
            l.rn = 1;

    -- NOTE(Dan): Finally, we combine the leaf allocations with ancestor allocations. We will charge what we subtracted
    -- from the child allocation, in every ancestor.

    create temporary table charge_result on commit drop as
        select
            leaves.id as leaf_id,
            leaves.associated_wallet as leaf_wallet,
            ancestor_alloc.id as local_id,
            subtracted as local_subtraction,
            ancestor_alloc.balance current_balance,
            ancestor_alloc.allocation_path,
            ancestor_alloc.associated_wallet as local_wallet,
            product_id,
            units, number_of_products, performed_by, description,
            payment_required, local_request_id, free_to_use, transaction_id
        from
            all_leaves leaves left join
            accounting.wallet_allocations ancestor_alloc on leaves.allocation_path <@ ancestor_alloc.allocation_path or free_to_use = true;

    create temporary table ancestor_charges on commit drop as
        select leaves.id as leaf_id,
            leaves.associated_wallet as leaf_wallet,
            ancestor_alloc.id as local_id,
            subtracted as local_subtraction,
            ancestor_alloc.balance current_balance,
            ancestor_alloc.allocation_path,
            ancestor_alloc.associated_wallet as local_wallet,
            product_id,
            units, number_of_products, performed_by, description,
            payment_required, local_request_id, free_to_use, transaction_id
        from
            all_leaves leaves left join
            accounting.wallet_allocations ancestor_alloc on leaves.allocation_path <@ ancestor_alloc.allocation_path or free_to_use = true
        where ancestor_alloc.id != leaves.id;

    create temporary table leaves_charges on commit drop as
        select
            leaves.id as leaf_id,
            leaves.associated_wallet as leaf_wallet,
            ancestor_alloc.id as local_id,
            subtracted as local_subtraction,
            ancestor_alloc.balance current_balance,
            ancestor_alloc.allocation_path,
            ancestor_alloc.associated_wallet as local_wallet,
            product_id,
            units, number_of_products, performed_by, description,
            payment_required, local_request_id, free_to_use, transaction_id
        from
            all_leaves leaves left join
            accounting.wallet_allocations ancestor_alloc on leaves.allocation_path <@ ancestor_alloc.allocation_path or free_to_use = true
        where ancestor_alloc.id = leaves.id;

    select count(distinct local_request_id) into charge_count from charge_result;
    if charge_count != (cardinality(requests)-invalid_transactions) then
        raise exception 'Unable to fulfill all requests. Permission denied/bad request.';
    end if;
end;
$$;

-- noinspection SqlResolve
create or replace function accounting.charge(
    requests accounting.charge_request[]
) returns setof int language plpgsql as $$
begin
    -- TODO(Dan): This is currently lacking replay protection
    perform accounting.process_charge_requests(requests);

    create temporary table failed_charges(request_index int) on commit drop;

    -- NOTE(Dan): Update the balance of every relevant allocation
    -- NOTE(Dan): We _must_ sum over the local_subtractions. During the update every read of balance will always
    -- return the same value, thus we would get the wrong results if we don't sum to the total change.
    with
        combined_balance_subtractions as (
            select local_id, sum(local_subtraction) local_subtraction
            from charge_result
            group by local_id
        ),
        updates as (
            update accounting.wallet_allocations alloc
            set balance = balance - local_subtraction
            from combined_balance_subtractions sub
            where alloc.id = sub.local_id
            returning balance - local_subtraction as new_balance, alloc.id as local_id
        )
    insert into failed_charges (request_index)
    select local_request_id
    from
        updates u join
        charge_result c on u.local_id = c.local_id
    where u.new_balance < 0 and free_to_use != true;

    insert into failed_charges (request_index)
    select local_request_id
    from duplicate_transactions;

    with
        combined_local_balance_subtractions as (
            select local_id, sum(case when leaf_id = local_id then local_subtraction else 0 end) local_subtraction
            from charge_result
            group by local_id
            having sum(case when leaf_id = local_id then local_subtraction else 0 end) != 0
        ),
        updates as (
            update accounting.wallet_allocations alloc
            set local_balance = local_balance - local_subtraction
            from combined_local_balance_subtractions sub
            where alloc.id = sub.local_id
            returning local_balance - local_subtraction as new_balance, alloc.id as local_id
        )
    insert into failed_charges (request_index)
    select local_request_id
    from
        updates u join
        charge_result c on u.local_id = c.local_id
    where new_balance < 0 and free_to_use != true;

    delete from ancestor_charges
    where local_request_id in (select local_request_id from failed_charges);

    insert into accounting.transactions
    (type, created_at, affected_allocation_id, action_performed_by, change, description,
         source_allocation_id, product_id, number_of_products, units, transaction_id, initial_transaction_id)
    select 'charge', now(), res.local_id , res.performed_by, -res.local_subtraction, res.description,
       res.leaf_id, res.product_id, res.number_of_products, res.units, res.transaction_id, res.transaction_id from leaves_charges res
    where res.local_subtraction != 0;

    -- NOTE(Dan): Insert a record of every change we did in the transactions table except free to use items
    insert into accounting.transactions
            (type, created_at, affected_allocation_id, action_performed_by, change, description,
             source_allocation_id, product_id, number_of_products, units, transaction_id, initial_transaction_id)
    select 'charge', now(), res.local_id, res.performed_by, -res.local_subtraction, res.description,
           res.leaf_id, res.product_id, res.number_of_products, res.units, uuid_generate_v4(), res.transaction_id
    from ancestor_charges res
    where free_to_use != true;

    return query select distinct request_index - 1 from failed_charges;
end;
$$;

-- noinspection SqlResolve
create or replace function accounting.credit_check(
    requests accounting.charge_request[]
) returns setof boolean language plpgsql as $$
begin
    perform accounting.process_charge_requests(requests);
    return query(
        select bool_and(has_enough_credits)
        from (
            select local_request_id, ((current_balance - local_subtraction) >= 0 or free_to_use) has_enough_credits
            from charge_result
            order by local_request_id
        ) t
        group by local_request_id
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
    description text,
    transaction_id text,
    application_id bigint
);

create or replace function accounting.deposit(
    requests accounting.deposit_request[]
) returns void language plpgsql as $$
declare
    deposit_count bigint;
begin
    create temporary table deposit_result on commit drop as
        -- NOTE(Dan): Resolve and verify source wallet, potentially resolve destination wallet
        with unpacked_requests as (
            select initiated_by, recipient, recipient_is_project, source_allocation, desired_balance, start_date,
                   end_date, description, transaction_id, application_id
            from unnest(requests)
        )
        select
            -- NOTE(Dan): we pre-allocate the IDs to make it easier to connect the data later
            nextval('accounting.wallet_allocations_id_seq') idx,
            source_wallet.id source_wallet,
            source_wallet.category product_category,
            source_alloc.allocation_path source_allocation_path,
            target_wallet.id target_wallet,
            request.recipient,
            request.recipient_is_project,
            coalesce(request.start_date, now()) start_date,
            request.end_date,
            request.desired_balance,
            request.initiated_by,
            source_wallet.category,
            request.description,
            request.transaction_id,
            request.application_id application_id
        from
            unpacked_requests request join
            accounting.wallet_allocations source_alloc on request.source_allocation = source_alloc.id join
            accounting.wallets source_wallet on source_alloc.associated_wallet = source_wallet.id join
            accounting.wallet_owner source_owner on source_wallet.owned_by = source_owner.id left join
            project.project_members pm on
                source_owner.project_id = pm.project_id and
                (pm.role = 'ADMIN' or pm.role = 'PI') left join

            accounting.wallet_owner target_owner on
                (request.recipient_is_project and target_owner.project_id = request.recipient) or
                (not request.recipient_is_project and target_owner.username = request.recipient) left join
            accounting.wallets target_wallet on
                target_wallet.owned_by = target_owner.id and
                target_wallet.category = source_wallet.category
        where
            (
                request.initiated_by = source_owner.username or
                request.initiated_by = pm.username
            );

    select count(*) into deposit_count from deposit_result;
    if deposit_count != cardinality(requests) then
        raise exception 'Unable to fulfill all requests. Permission denied/bad request.';
    end if;

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
        w.category = r.category and
        (
            (r.recipient_is_project and wo.project_id = r.recipient) or
            (not r.recipient_is_project and wo.username = r.recipient)
        );

    insert into accounting.deposit_notifications (username, project_id, category_id, balance)
    select
        case
            when r.recipient_is_project = true then null
            else r.recipient
        end,
        case
            when r.recipient_is_project = true then r.recipient
            else null
        end,
        r.category,
        r.desired_balance
    from deposit_result r;

    -- NOTE(Dan): Create allocations and insert transactions
    with new_allocations as (
        insert into accounting.wallet_allocations
            (id, associated_wallet, balance, initial_balance, local_balance, start_date, end_date,
             allocation_path, granted_in)
        select
            r.idx,
            r.target_wallet,
            r.desired_balance,
            r.desired_balance,
            r.desired_balance,
            r.start_date,
            r.end_date,
            (r.source_allocation_path::text || '.' || r.idx::text)::ltree,
            r.application_id
        from deposit_result r
        where r.target_wallet is not null
        returning id, balance
    )
    insert into accounting.transactions
    (type, affected_allocation_id, action_performed_by, change, description, start_date, transaction_id, initial_transaction_id)
    select 'deposit', alloc.id, r.initiated_by, alloc.balance, r.description , now(), r.transaction_id, r.transaction_id
    from
        new_allocations alloc join
        deposit_result r on alloc.id = r.idx;

    update accounting.wallets
    set low_funds_notifications_send = false
    from deposit_result r
    where r.target_wallet = id;
end;
$$;

create type accounting.allocation_update_request as (
    performed_by text,
    allocation_id bigint,
    start_date timestamptz,
    end_date timestamptz,
    description text,
    balance bigint,
    transaction_id text
);

create or replace function accounting.update_allocations(
    request accounting.allocation_update_request[]
) returns void language plpgsql as $$
begin
    create temporary table update_result on commit drop as
    with requests as (
        select performed_by, allocation_id, start_date, end_date, description, balance, transaction_id, row_number() over () request_idx
        from unnest(request)
    )
    select
        alloc.id as alloc_id, parent.id as parent_id, descendant.id as descandant_id,
        alloc.balance as alloc_balance, descendant.balance as descendant_balance,
        req.performed_by, req.start_date, req.end_date, req.description, req.balance, request_idx,
        pc.charge_type, alloc.initial_balance as alloc_initial_balance, req.transaction_id
    from
        requests req join
        accounting.wallet_allocations alloc on allocation_id = alloc.id join
        accounting.wallets wallet on alloc.associated_wallet = wallet.id join
        accounting.product_categories pc on wallet.category = pc.id join

        -- NOTE(Dan): Find the parent allocation and the parent's owner
        accounting.wallet_allocations parent on
            parent.id = subpath(alloc.allocation_path, nlevel(alloc.allocation_path) - 2, 1)::text::bigint and
            parent.id != alloc.id join
        accounting.wallets parent_wallet on parent.associated_wallet = parent_wallet.id join
        accounting.wallet_owner parent_owner on parent_owner.id = parent_wallet.owned_by left join
        project.project_members pm on
            parent_owner.project_id = pm.project_id and
            pm.username = req.performed_by and
            (pm.role = 'ADMIN' or pm.role = 'PI') left join

        -- NOTE(Dan): Find descendants with non-overlapping allocation periods
        accounting.wallet_allocations descendant on
            alloc.allocation_path @> descendant.allocation_path and
            (
                (req.start_date > descendant.start_date) or
                (req.end_date is not null and descendant.end_date is null) or
                (req.end_date is not null and req.end_date > descendant.end_date)
            ) and
            (
                descendant.start_date is distinct from req.start_date or
                descendant.end_date is distinct from req.end_date
            )
    where
        -- NOTE(Dan): Check that we are allowed to act on the parent's behalf
        req.performed_by = pm.username or
        req.performed_by = parent_owner.username;

    if (select count(distinct request_idx) from update_result) != cardinality(request) then
        raise exception 'Unable to fulfill all requests. Permission denied/bad request.';
    end if;

    -- NOTE(Dan): Update the descendants. This needs to happen bottom-up.
    with update_table as (
        select descandant_id, start_date, end_date
        from update_result
        where descandant_id is not null
        order by descandant_id desc -- NOTE(Dan): Order by bottom-up.
    )
    update accounting.wallet_allocations alloc
    set
        start_date = update_table.start_date,
        end_date = update_table.end_date
    from update_table
    where
        alloc.id = update_table.descandant_id;

    -- NOTE(Dan): Update the target allocation
    with update_table as (
        select distinct alloc_id, start_date, end_date, balance, charge_type
        from update_result
    )
    update accounting.wallet_allocations alloc
    set
        start_date = update_table.start_date,
        end_date = update_table.end_date,
        balance = case
            when charge_type = 'DIFFERENTIAL_QUOTA' then
                update_table.balance - (alloc.initial_balance - alloc.balance)
            else update_table.balance
        end,
        initial_balance = update_table.balance,
        local_balance = case
            when charge_type = 'DIFFERENTIAL_QUOTA' then
                update_table.balance - (alloc.initial_balance - alloc.local_balance)
            else update_table.balance
        end
    from update_table
    where alloc.id = update_table.alloc_id;

    -- NOTE(Dan): Insert transactions for all descendants
    insert into accounting.transactions
        (type, affected_allocation_id, action_performed_by, change, description,
        start_date, end_date, transaction_id, initial_transaction_id)
    select 'allocation_update'::accounting.transaction_type, u.descandant_id, u.performed_by, 0, u.description,
           u.start_date, u.end_date, u.transaction_id, u.transaction_id
    from update_result u
    where u.descandant_id is not null and u.descandant_id != u.alloc_id;

    -- NOTE(Dan): Insert transactions for all target allocations
    insert into accounting.transactions
        (type, affected_allocation_id, action_performed_by, change, description,
        start_date, end_date, transaction_id, initial_transaction_id)
    select distinct
        'allocation_update'::accounting.transaction_type, u.alloc_id, u.performed_by,
        case
            when charge_type = 'DIFFERENTIAL_QUOTA' then
                (u.balance - (u.alloc_initial_balance - u.alloc_balance)) - u.alloc_balance
            else u.balance - u.alloc_balance
        end,
        u.description, u.start_date, u.end_date, u.transaction_id, u.transaction_id
    from update_result u;
end;
$$;

create or replace function accounting.wallet_owner_to_json(
    owner_in accounting.wallet_owner
) returns jsonb language sql as $$
    select jsonb_build_object(
        'type', case
            when owner_in.username is not null then 'user'
            else 'project'
        end,
        'username', owner_in.username,
        'projectId', owner_in.project_id
    );
$$;

create or replace function accounting.product_category_to_json(
    category_in accounting.product_categories
) returns jsonb language sql as $$
    select jsonb_build_object(
        'name', category_in.category,
        'provider', category_in.provider
    );
$$;

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
            'memoryInGigs', product_in.memory_in_gigs
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

    return builder;
end
$$;

create or replace function accounting.wallet_allocation_to_json(
    allocation_in accounting.wallet_allocations
) returns jsonb language sql as $$
    select jsonb_build_object(
        'id', allocation_in.id::text,
        'allocationPath', regexp_split_to_array(allocation_in.allocation_path::text, '\.'),
        'balance', allocation_in.balance,
        'initialBalance', allocation_in.initial_balance,
        'localBalance', allocation_in.local_balance,
        'startDate', floor(extract(epoch from allocation_in.start_date) * 1000),
        'endDate', floor(extract(epoch from allocation_in.end_date) * 1000),
        'grantedIn', allocation_in.granted_in
    );
$$;

create or replace function accounting.wallet_to_json(
    wallet_in accounting.wallets,
    owner_in accounting.wallet_owner,
    allocations_in accounting.wallet_allocations[],
    category_in accounting.product_categories
) returns jsonb language sql as $$
    select jsonb_build_object(
        'owner', accounting.wallet_owner_to_json(owner_in),
        'paysFor', accounting.product_category_to_json(category_in),
        'chargePolicy', wallet_in.allocation_selector_policy,
        'allocations', (
            select coalesce(jsonb_agg(accounting.wallet_allocation_to_json(alloc)), '[]'::jsonb)
            from (
                select alloc
                from unnest(allocations_in) alloc
                order by alloc.id
            ) t
        ),
        'productType', category_in.product_type,
        'chargeType', category_in.charge_type,
        'unit', category_in.unit_of_price
    )
$$;

create or replace function accounting.transaction_to_json(
    transaction_in accounting.transactions,
    product_in accounting.products,
    category_in accounting.product_categories
) returns jsonb language plpgsql as $$
declare
    builder jsonb;
begin
    builder := jsonb_build_object(
        'type', transaction_in.type,
        'change', transaction_in.change,
        'actionPerformedBy', transaction_in.action_performed_by,
        'description', transaction_in.description,
        'affectedAllocationId', transaction_in.affected_allocation_id::text,
        'timestamp', floor(extract(epoch from transaction_in.created_at) * 1000),
        'transactionId', transaction_in.transaction_id,
        'initialTransactionId', transaction_in.initial_transaction_id,
        'resolvedCategory', case
            when category_in is null then null
            else accounting.product_category_to_json(category_in)
        end
    );

    if transaction_in.type = 'charge' then
        builder := builder || jsonb_build_object(
            'sourceAllocationId', transaction_in.source_allocation_id::text,
            'productId', product_in.name,
            'numberOfProducts', transaction_in.number_of_products,
            'units', transaction_in.units
        );
    elseif transaction_in.type = 'deposit' then
        builder := builder || jsonb_build_object(
            'sourceAllocationId', transaction_in.source_allocation_id::text,
            'startDate', floor(extract(epoch from transaction_in.start_date) * 1000),
            'endDate', floor(extract(epoch from transaction_in.end_date) * 1000)
        );
    elseif transaction_in.type = 'transfer' then
         builder := builder || jsonb_build_object(
            'sourceAllocationId', transaction_in.source_allocation_id::text,
            'startDate', floor(extract(epoch from transaction_in.start_date) * 1000),
            'endDate', floor(extract(epoch from transaction_in.end_date) * 1000)
        );
    elseif transaction_in.type = 'allocation_update' then
        builder := builder || jsonb_build_object(
            'startDate', floor(extract(epoch from transaction_in.start_date) * 1000),
            'endDate', floor(extract(epoch from transaction_in.end_date) * 1000)
        );
    end if;

    return builder;
end;
$$;

create type accounting.transfer_request as (
    source text,
    source_is_project boolean,
    target text,
    target_is_project boolean,
    units bigint,
    product_cat_name text,
    product_provider text,
    start_date timestamptz,
    end_date timestamptz,
    performed_by text,
    description text,
    transaction_id text
);

create or replace function accounting.process_transfer_requests(
    requests accounting.transfer_request[]
) returns void language plpgsql as $$
declare
    charge_count bigint;
begin
    drop table if exists transfer_result;
    create temporary table transfer_result on commit drop as
        with requests as (
                -- NOTE(Dan): DataGrip/IntelliJ thinks these are unresolved. They are not.
                select source, source_is_project, target, target_is_project, units, product_cat_name,
                        product_provider, start_date, end_date, performed_by, description, transaction_id,
                        row_number() over () local_request_id
                from unnest(requests) r
                where source != target
            ),
            -- NOTE(Dan): product_and_price finds the product relevant for this request. It is used later to resolve the
            -- correct price and resolve the correct wallets.
            product_and_price as (
                select
                    pc.id product_category,
                    request.units as payment_required,
                    request.*
                from
                    requests request join
                    accounting.product_categories pc on
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
                    units, performed_by, description,
                    payment_required, local_request_id,
                    start_date, end_date, p.transaction_id
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
                            accounting.wallets w on
                                alloc.associated_wallet = w.id and
                                w.category = p.product_category join
                            accounting.wallet_owner wo on w.owned_by = wo.id
                        where
                            (
                                (source_is_project and wo.project_id = source) or
                                (not source_is_project and wo.username = source)
                            ) and
                            (now() >= alloc.start_date and alloc.start_date >= start_date) and
                            (alloc.end_date is null or now() <= alloc.end_date or alloc.end_date >= end_date)
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
        units, performed_by, description,
        payment_required, local_request_id,
        leaves.start_date, leaves.end_date, leaves.transaction_id
    from
        leaf_charges leaves join
        accounting.wallet_allocations ancestor_alloc on leaves.allocation_path <@ ancestor_alloc.allocation_path;

    select count(distinct local_request_id) into charge_count from transfer_result;
    if charge_count != cardinality(requests) then
        raise exception 'Unable to fulfill all requests. Permission denied/bad request';
    end if;
end;
$$;

-- noinspection SqlResolve
create or replace function accounting.transfer(
    requests accounting.transfer_request[]
) returns void language plpgsql as $$
begin
    -- TODO(Dan): This is currently lacking replay protection
    perform accounting.process_transfer_requests(requests);

    -- NOTE(Dan): Update the balance of every relevant allocation
    update accounting.wallet_allocations alloc
    set balance = balance - res.local_subtraction
    from transfer_result res
    where alloc.id = res.local_id;

    -- NOTE(Dan): Insert a record of every change we did in the transactions table
    insert into accounting.transactions
            (type, created_at, affected_allocation_id, action_performed_by, change, description,
                source_allocation_id, product_id, number_of_products, units, start_date, end_date, transaction_id, initial_transaction_id)
    select 'transfer', now(), res.local_id, res.performed_by, -res.local_subtraction, res.description,
           res.leaf_id, null, null, null, coalesce(res.start_date, now()), res.end_date, res.transaction_id, res.transaction_id
    from transfer_result res;
end;
$$;

--NOTE(Henrik) Operator overload will not replace old credit_check
create or replace function accounting.credit_check(
    requests accounting.transfer_request[]
) returns setof boolean language plpgsql as $$
begin
    perform accounting.process_transfer_requests(requests);
    return query (
        select sum(group_subtraction)::bigint = payment_required has_enough_credits
        from (
            select min(local_subtraction) group_subtraction, payment_required, local_request_id
            from transfer_result
            group by leaf_id, payment_required, local_request_id
        ) min_per_group
        group by local_request_id, payment_required
    );
end;
$$;

create or replace function provider.timestamp_to_unix(ts timestamptz) returns double precision language sql immutable as $$
    select (floor(extract(epoch from ts) * 1000));
$$;

-- Create a function that always returns the first non-NULL item
create or replace function provider.first_agg (anyelement, anyelement)
returns anyelement language sql immutable strict parallel safe as $$
        select $1;
$$;

-- And then wrap an aggregate around it
create aggregate provider.first (
        sfunc    = provider.first_agg,
        basetype = anyelement,
        stype    = anyelement
);

-- Create a function that always returns the last non-NULL item
create or replace function provider.last_agg ( anyelement, anyelement )
returns anyelement language sql immutable strict parallel safe as $$
        select $2;
$$;

-- And then wrap an aggregate around it
create aggregate provider.last (
        sfunc    = provider.last_agg,
        basetype = anyelement,
        stype    = anyelement
);

create or replace function project.find_by_path(path_in text) returns project.projects[] language plpgsql as $$
declare
    i int;
    parent_needed text := null;
    current_project text;
    component text;
    components text[];
    result project.projects[];
begin
    components := regexp_split_to_array(path_in, '/');
    for i in array_lower(components, 1)..array_upper(components, 1) loop
        if i > 0 then
            parent_needed := result[i - 1].id;
        end if;

        component := components[i];

        select p into current_project
        from project.projects p
        where
            upper(title) = upper(component) and
            parent is not distinct from parent_needed;

        if current_project is null then
            return null;
        end if;

        result[i] := current_project;
    end loop;
    return result;
end;
$$;

---- /Procedures ----

---- Grants ----

drop function "grant".grant_recipient_title(grant_recipient text, grant_recipient_type text);
drop function "grant".my_applications(username_in text, include_incoming boolean, include_outgoing boolean);
drop function "grant".transfer_application(actor text, application_id bigint, target_project text);

alter table "grant".allow_applications_from add foreign key (project_id) references project.projects(id);
alter table "grant".allow_applications_from drop constraint allow_applications_from_pkey;
alter table "grant".allow_applications_from alter column applicant_id drop not null;
update "grant".allow_applications_from
set applicant_id = null
where type = 'anyone';
create unique index allow_applications_from_uniq on "grant".allow_applications_from (project_id, type, coalesce(applicant_id, ''));

alter table "grant".applications add foreign key (resources_owned_by) references project.projects(id);
alter table "grant".applications add foreign key (requested_by) references auth.principals(id);
update "grant".applications set status_changed_by = '_ucloud' where status_changed_by = '_UCloud';
alter table "grant".applications add foreign key (status_changed_by) references auth.principals(id);
alter table "grant".applications add column reference_id text unique default null;

alter table "grant".automatic_approval_limits add foreign key (project_id) references project.projects(id);
alter table "grant".automatic_approval_limits add column product_category2 bigint references accounting.product_categories(id);
update "grant".automatic_approval_limits l
set product_category2 = pc.id
from accounting.product_categories pc
where
    l.product_category = pc.category and
    l.product_provider = pc.provider;
alter table "grant".automatic_approval_limits drop column product_category;
alter table "grant".automatic_approval_limits drop column product_provider;
alter table "grant".automatic_approval_limits alter column product_category2 set not null;
alter table "grant".automatic_approval_limits rename column product_category2 to product_category;

alter table "grant".automatic_approval_users add foreign key (project_id) references project.projects(id);

alter table "grant".comments add foreign key (posted_by) references auth.principals(id);

alter table "grant".descriptions add foreign key (project_id) references project.projects(id);

alter table "grant".exclude_applications_from add foreign key (project_id) references project.projects(id);

alter table "grant".gift_resources add column product_category2 bigint references accounting.product_categories(id);
update "grant".gift_resources l
set product_category2 = pc.id
from accounting.product_categories pc
where
    l.product_category = pc.category and
    l.product_provider = pc.provider;
alter table "grant".gift_resources drop column product_category;
alter table "grant".gift_resources drop column product_provider;
alter table "grant".gift_resources alter column product_category2 set not null;
alter table "grant".gift_resources rename column product_category2 to product_category;

alter table "grant".gifts add foreign key (resources_owned_by) references project.projects(id);

with invalid_gift_claimers as (
    select gc.gift_id as invalid_id, gc.user_id invalid_user
    from
        "grant".gifts_claimed gc left join
        auth.principals p on gc.user_id = p.id
    where
        p.id is null
)
delete from "grant".gifts_claimed gc
using invalid_gift_claimers
where user_id = invalid_user and gift_id = invalid_id;

alter table "grant".gifts_claimed add foreign key (user_id) references auth.principals(id);

alter table "grant".gifts_user_criteria drop constraint gifts_user_criteria_pkey;
alter table "grant".gifts_user_criteria alter column applicant_id drop not null;
update "grant".gifts_user_criteria
set applicant_id = null
where type = 'anyone';
create unique index gifts_user_criteria_uniq on "grant".gifts_user_criteria (gift_id, type, coalesce(applicant_id, ''));


alter table "grant".is_enabled add foreign key (project_id) references project.projects(id);

alter table "grant".logos add foreign key (project_id) references project.projects(id);

alter table "grant".requested_resources add column product_category2 bigint references accounting.product_categories(id);
update "grant".requested_resources l
set product_category2 = pc.id
from accounting.product_categories pc
where
    l.product_category = pc.category and
    l.product_provider = pc.provider;
alter table "grant".requested_resources drop column product_category;
alter table "grant".requested_resources drop column product_provider;
alter table "grant".requested_resources alter column product_category2 set not null;
alter table "grant".requested_resources rename column product_category2 to product_category;

alter table "grant".templates add foreign key (project_id) references project.projects(id);

create or replace function "grant".resource_request_to_json(
    request_in "grant".requested_resources,
    product_category_in accounting.product_categories
) returns jsonb language sql as $$
    select jsonb_build_object(
        'productCategory', product_category_in.category,
        'productProvider', product_category_in.provider,
        'balanceRequested', request_in.credits_requested
    );
$$;

create or replace function "grant".application_to_json(
    application_in "grant".applications,
    resources_in jsonb[],
    resources_owned_by_in project.projects,
    project_in project.projects,
    project_pi_in text
) returns jsonb language plpgsql as $$
declare
    builder jsonb;
begin
    builder := jsonb_build_object(
        'status', application_in.status,
        'resourcesOwnedBy', application_in.resources_owned_by,
        'requestedBy', application_in.requested_by,
        'document', application_in.document,
        'id', application_in.id,
        'createdAt', (floor(extract(epoch from application_in.created_at) * 1000)),
        'updatedAt', (floor(extract(epoch from application_in.updated_at) * 1000)),
        'statusChangedBy', application_in.status_changed_by,
        'requestedResources', resources_in,
        'resourcesOwnedByTitle', resources_owned_by_in.title,
        'referenceId', application_in.reference_id
    );

    if application_in.grant_recipient_type = 'personal' then
        builder := builder || jsonb_build_object(
            'grantRecipient', jsonb_build_object(
                'type', application_in.grant_recipient_type,
                'username', application_in.grant_recipient
            ),
            'grantRecipientPi', application_in.grant_recipient,
            'grantRecipientTitle', application_in.grant_recipient
        );
    elseif application_in.grant_recipient_type = 'existing_project' then
        builder := builder || jsonb_build_object(
            'grantRecipient', jsonb_build_object(
                'type', application_in.grant_recipient_type,
                'projectId', application_in.grant_recipient
            ),
            'grantRecipientPi', project_pi_in,
            'grantRecipientTitle', project_in.title
        );
    elseif application_in.grant_recipient_type = 'new_project' then
        builder := builder || jsonb_build_object(
            'grantRecipient', jsonb_build_object(
                'type', application_in.grant_recipient_type,
                'projectTitle', application_in.grant_recipient
            ),
            'grantRecipientPi', application_in.requested_by,
            'grantRecipientTitle', application_in.grant_recipient
        );
    end if;

    return builder;
end;
$$;


create or replace function "grant".can_submit_application(
    username_in text,
    source text,
    grant_recipient text,
    grant_recipient_type text
) returns boolean language sql as $$
    with
        non_excluded_user as (
            select
                requesting_user.id, requesting_user.email, requesting_user.org_id
            from
                auth.principals requesting_user left join
                "grant".exclude_applications_from exclude_entry on
                    requesting_user.email like '%@' || exclude_entry.email_suffix and
                    exclude_entry.project_id = source
            where
                requesting_user.id = username_in
            group by
                requesting_user.id, requesting_user.email, requesting_user.org_id
            having
                count(email_suffix) = 0
        ),
        allowed_user as (
            select user_info.id
            from
                non_excluded_user user_info join
                "grant".allow_applications_from allow_entry on
                    allow_entry.project_id = source and
                    (
                        (
                            allow_entry.type = 'anyone'
                        ) or

                        (
                            allow_entry.type = 'wayf' and
                            allow_entry.applicant_id = user_info.org_id
                        ) or

                        (
                            allow_entry.type = 'email' and
                            user_info.email like '%@' || allow_entry.applicant_id
                        )
                    )
        ),

        existing_project_is_parent as (
            select existing_project.id
            from
                project.projects source_project join
                project.projects existing_project on
                    source_project.id = source and
                    source_project.id = existing_project.parent and
                    grant_recipient_type = 'existing_project' and
                    existing_project.id = grant_recipient join
                project.project_members pm on
                    pm.username = username_in and
                    pm.project_id = existing_project.id and
                    (
                        pm.role = 'ADMIN' or
                        pm.role = 'PI'
                    )
        )
    select coalesce(bool_or(allowed), false)
    from (
        select true allowed
        from
            allowed_user join
            "grant".is_enabled on
                is_enabled.project_id = source
        where
            allowed_user.id is not null or
            exists (select * from existing_project_is_parent)
    ) t
$$;

create or replace function "grant".application_status_trigger() returns trigger language plpgsql as $$
begin
    --
    if (
        (new.status_changed_by = old.status_changed_by) and
        (new.status = old.status) and
        (new.created_at = old.created_at) and
        (new.document = old.document) and
        (new.grant_recipient = old.grant_recipient) and
        (new.grant_recipient_type = old.grant_recipient_type) and
        (new.requested_by = old.requested_by) and
        (new.resources_owned_by = old.resources_owned_by) and
        (new.updated_at = old.updated_at) and
        (new.reference_id != old.reference_id)
        ) then
            return null;
    end if;
    if old.status = 'APPROVED' or old.status = 'REJECTED' then
        raise exception 'Cannot update a closed application';
    end if;
    return null;
end;
$$;

create trigger application_status_trigger
after update on "grant".applications
for each row execute procedure "grant".application_status_trigger();

create or replace function "grant".approve_application(
    approved_by text,
    application_id_in bigint
) returns void language plpgsql as $$
declare
    created_project text;
begin
    -- NOTE(Dan): Start by finding all source allocations and target allocation information.
    -- NOTE(Dan): We currently pick source allocation using an "expire first" policy. This might need to change in the
    -- future.
    create temporary table approve_result on commit drop as
    select *
    from (
        select
            approved_by,
            app.id application_id,
            app.grant_recipient,
            app.grant_recipient_type,
            app.requested_by,
            app.resources_owned_by,
            alloc.id allocation_id,
            w.category,
            resource.quota_requested_bytes,
            resource.credits_requested,
            alloc.start_date,
            alloc.end_date,
            row_number() over (partition by w.category order by alloc.end_date nulls last, alloc.id) alloc_idx
        from
            "grant".applications app join
            "grant".requested_resources resource on app.id = resource.application_id join
            accounting.wallet_owner wo on app.resources_owned_by = wo.project_id join
            accounting.wallets w on wo.id = w.owned_by and w.category = resource.product_category join
            accounting.wallet_allocations alloc on
                w.id = alloc.associated_wallet and
                now() >= alloc.start_date and
                (alloc.end_date is null or now() <= alloc.end_date)
        where
            app.status = 'APPROVED' and
            app.id = application_id_in
    ) t
    where
        t.alloc_idx = 1;

    -- NOTE(Dan): Create a project, if the grant_recipient_type = 'new_project'
    insert into project.projects (id, created_at, modified_at, title, archived, parent, dmp, subprojects_renameable)
    select uuid_generate_v4()::text, now(), now(), grant_recipient, false, resources_owned_by, null, false
    from approve_result
    where grant_recipient_type = 'new_project'
    limit 1
    returning id into created_project;

    insert into project.project_members (created_at, modified_at, role, username, project_id)
    select now(), now(), 'PI', requested_by, created_project
    from approve_result
    where grant_recipient_type = 'new_project'
    limit 1;

    -- NOTE(Dan): Run the normal deposit procedure
    perform accounting.deposit(array_agg(req))
    from (
        select (
            result.approved_by,
            case
                when result.grant_recipient_type = 'new_project' then created_project
                when result.grant_recipient_type = 'existing_project' then result.grant_recipient
                else result.grant_recipient
            end,
            case
                when result.grant_recipient_type = 'new_project' then true
                when result.grant_recipient_type = 'existing_project' then true
                else false
            end,
            allocation_id,
            coalesce(result.credits_requested, result.quota_requested_bytes),
            result.start_date,
            result.end_date,
            'Grant application approved',
            concat(result.approved_by, '-', uuid_generate_v4()),
            result.application_id
        )::accounting.deposit_request req
        from approve_result result
    ) t;
end;
$$;

create or replace function "grant".comment_to_json(
    comment_in "grant".comments
) returns jsonb language sql immutable as $$
    select case when comment_in is null then null else jsonb_build_object(
        'id', comment_in.id,
        'postedBy', comment_in.posted_by,
        'postedAt', (floor(extract(epoch from comment_in.created_at) * 1000)),
        'comment', comment_in.comment
    ) end
$$;

create or replace function "grant".upload_request_settings(
    actor_in text,
    project_in text,

    new_exclude_list_in text[],

    new_include_list_type_in text[],
    new_include_list_entity_in text[],

    auto_approve_from_type_in text[],
    auto_approve_from_entity_in text[],
    auto_approve_resource_cat_name_in text[],
    auto_approve_resource_provider_name_in text[],
    auto_approve_credits_max_in bigint[],
    auto_approve_quota_max_in bigint[]
) returns void language plpgsql as $$
declare
    can_update boolean := false;
begin
    if project_in is null then
        raise exception 'Missing project';
    end if;

    select count(*) > 0 into can_update
    from
        project.project_members pm join
        "grant".is_enabled enabled on pm.project_id = enabled.project_id
    where
        pm.username = actor_in and
        (pm.role = 'ADMIN' or pm.role = 'PI') and
        pm.project_id = project_in;

    if not can_update then
        raise exception 'Unable to update this project. Check if you are allowed to perform this operation.';
    end if;

    delete from "grant".exclude_applications_from
    where project_id = project_in;

    insert into "grant".exclude_applications_from (project_id, email_suffix)
    select project_in, unnest(new_exclude_list_in);

    delete from "grant".allow_applications_from
    where project_id = project_in;

    insert into "grant".allow_applications_from (project_id, type, applicant_id)
    select project_in, unnest(new_include_list_type_in), unnest(new_include_list_entity_in);

    delete from "grant".automatic_approval_users
    where project_id = project_in;

    insert into "grant".automatic_approval_users (project_id, type, applicant_id)
    select project_in, unnest(auto_approve_from_type_in), unnest(auto_approve_from_entity_in);

    delete from "grant".automatic_approval_limits
    where project_id = project_in;

    insert into "grant".automatic_approval_limits (project_id, maximum_credits, maximum_quota_bytes, product_category)
    with entries as (
        select
            unnest(auto_approve_resource_cat_name_in) category,
            unnest(auto_approve_resource_provider_name_in) provider,
            unnest(auto_approve_credits_max_in) credits,
            unnest(auto_approve_quota_max_in) quota
    )
    select project_in, credits, quota, pc.id
    from entries e join accounting.product_categories pc on e.category = pc.category and e.provider = pc.provider;
end;
$$;

create or replace function "grant".create_gift(
    actor_in text,
    gift_resources_owned_by_in text,
    title_in text,
    description_in text,

    criteria_type_in text[],
    criteria_entity_in text[],

    resource_cat_name_in text[],
    resource_provider_name_in text[],
    resources_credits_in bigint[],
    resources_quota_in bigint[]
) returns bigint language plpgsql as $$
declare
    can_create_gift boolean := false;
    created_gift_id bigint;
begin
    select count(*) > 0 into can_create_gift
    from
        project.project_members pm
    where
        pm.project_id = gift_resources_owned_by_in and
        pm.username = actor_in and
        (pm.role = 'ADMIN' or pm.role = 'PI');

    if not can_create_gift then
        raise exception 'Unable to create a gift in this project. Are you an admin?';
    end if;

    insert into "grant".gifts (resources_owned_by, title, description)
    values (gift_resources_owned_by_in, title_in, description_in)
    returning id into created_gift_id;

    insert into "grant".gifts_user_criteria (gift_id, type, applicant_id)
    select created_gift_id, unnest(criteria_type_in), unnest(criteria_entity_in);

    insert into "grant".gift_resources (gift_id, credits, quota, product_category)
    with entries as (
        select unnest(resource_cat_name_in) category, unnest(resource_provider_name_in) provider,
               unnest(resources_quota_in) quota, unnest(resources_credits_in) credits
    )
    select created_gift_id, e.credits, e.quota, pc.id
    from
        entries e join
        accounting.product_categories pc on
            e.category = pc.category and
            e.provider = pc.provider;

    return created_gift_id;
end;
$$;

create or replace function "grant".delete_gift(
    actor_in text,
    gift_id_in bigint
) returns void language plpgsql as $$
declare
    can_delete_gift boolean := false;
begin
    select count(*) > 0 into can_delete_gift
    from
        project.project_members pm join
        "grant".gifts gift on
            gift.id = gift_id_in and
            pm.project_id = gift.resources_owned_by and
            pm.username = actor_in and
            (pm.role = 'PI' or pm.role = 'ADMIN');

    if not can_delete_gift then
        raise exception 'Unable to delete gift. Are you an admin?';
    end if;

    delete from "grant".gifts_claimed where gift_id = gift_id_in;
    delete from "grant".gift_resources where gift_id = gift_id_in;
    delete from "grant".gifts_user_criteria where gift_id = gift_id_in;
    delete from "grant".gifts where id = gift_id_in;
end;
$$;


create or replace function "grant".transfer_application(
    actor_in text,
    application_id_in bigint,
    target_project_in text
) returns void language plpgsql as $$
declare
    affected_application record;
    update_count int;
begin
    select
        resources_owned_by, grant_recipient, grant_recipient_type, requested_by into affected_application
    from
        "grant".applications app join
        project.project_members pm on
            app.resources_owned_by = pm.project_id and
            pm.username = actor_in and
            (pm.role = 'PI' or pm.role = 'ADMIN')
    where
        id = application_id_in;

    update "grant".applications
    set resources_owned_by = target_project_in
    where
        "grant".can_submit_application(affected_application.requested_by, target_project_in,
            affected_application.grant_recipient, affected_application.grant_recipient_type)
    returning 1 into update_count;

    if update_count is null then
        raise exception 'Unable to transfer application (Not found or permission denied)';
    end if;

    if target_project_in = affected_application.resources_owned_by then
        raise exception 'Unable to transfer application to itself';
    end if;

    delete from "grant".requested_resources res
    using
        "grant".applications app join
        accounting.wallet_owner source_owner on
            app.id = res.application_id and
            source_owner.project_id = app.resources_owned_by join
        accounting.wallets source_wallet on
            res.product_category = source_wallet.category  and
            source_owner.id = source_wallet.owned_by join

        accounting.wallet_owner target_owner on
            target_owner.project_id = target_project_in left join
        accounting.wallets target_wallet on
            source_wallet.category = target_wallet.category and
            target_owner.project_id = target_wallet.owned_by
    where
        res.application_id = application_id_in and
        target_wallet.id is null;
end;
$$;


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
            p.free_to_use,
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
            p.free_to_use,
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

---- /Grants ----
