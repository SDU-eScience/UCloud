create table accounting.wallet_samples_v2(
    sampled_at timestamptz not null default now(),
    wallet_id int8 not null references accounting.wallets(id),

    quota int8 not null,
    local_usage int8 not null,
    excess_usage int8 not null default 0,
    tree_usage int8 not null,

    group_ids int4[] not null,
    tree_usage_by_group int8[] not null,
    quota_by_group int8[] not null
);

create index on accounting.wallet_samples_v2(sampled_at);
create index on accounting.wallet_samples_v2(wallet_id);
