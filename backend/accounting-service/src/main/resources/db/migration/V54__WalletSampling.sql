create table accounting.wallet_samples(
    sampled_at timestamptz not null default now(),
    wallet_id int8 not null references accounting.wallets(id),
    local_usage int8 not null,
    tree_usage int8 not null,
    quota int8 not null,
    allocation_ids int4[] not null,
    local_usage_by_allocation int8[] not null,
    tree_usage_by_allocation int8[] not null,
    quota_by_allocation int8[] not null
);

create index on accounting.wallet_samples(sampled_at);
create index on accounting.wallet_samples(wallet_id);

create table accounting.breakdown_samples(
    sampled_at timestamptz not null default now(),
    wallet_id int8 not null references accounting.wallets(id),
    local_usage int8 not null,
    references_are_projects bool not null,
    bucket_references int4[] not null,
    bucket_usage int8[] not null
);

create index on accounting.breakdown_samples(sampled_at);
create index on accounting.breakdown_samples(wallet_id);
