alter table accounting.wallet_samples_v2
    add column retired_usage bigint default 0,
    add column retired_tree_usage bigint default 0,
    add column total_allocated bigint default 0,
    add column total_retired_allocation bigint default 0,
    add column was_locked bool default false,
    add column retried_tree_usage_by_group int8[] default array[]::int8[]
;