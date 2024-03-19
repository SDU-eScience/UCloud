drop table if exists accounting.wallet_samples;

alter table "grant".requested_resources drop column source_allocation;

create table accounting.wallets_v2 (
    id bigserial primary key,
    wallet_owner bigint references accounting.wallet_owner(id),
    product_category bigint references accounting.product_categories(id),
    local_usage bigint not null default 0,
    local_retired_usage bigint not null default 0,
    excess_usage bigint not null default 0,
    total_allocated bigint not null default 0,
    total_retired_allocated bigint not null default 0
);

create table accounting.allocation_groups (
    id bigserial primary key,
    parent_wallet bigint references accounting.wallets_v2(id),
    associated_wallet bigint references accounting.wallets_v2(id) not null,
    tree_usage bigint not null default  0,
    retired_tree_usage bigint not null default 0
);

create table accounting.wallet_allocations_v2 (
    id bigserial primary key,
    associated_allocation_group bigint references accounting.allocation_groups(id),
    granted_in bigint references "grant".applications(id),
    quota bigint not null,
    allocation_start_time timestamptz not null,
    allocation_end_time timestamptz not null,
    retired bool not null default false,
    retired_usage bigint default null
);

create table accounting.intermediate_usage (
    id bigserial primary key ,
    wallet_id bigint not null,
    usage bigint not null
);

drop function accounting.low_funds_wallets(name text, computecreditslimit bigint, storagecreditslimit bigint, computeunitslimit bigint, storagequotalimit bigint, storageunitslimit bigint);

