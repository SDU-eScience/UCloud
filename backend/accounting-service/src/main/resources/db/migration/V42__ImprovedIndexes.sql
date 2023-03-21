create index if not exists alloc_wallet_idx on accounting.wallet_allocations (associated_wallet);
create index if not exists alloc_path_idx on accounting.wallet_allocations using gist (allocation_path);
