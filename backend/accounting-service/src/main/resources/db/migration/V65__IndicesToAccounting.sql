create index associated_wallet_to_group on accounting.allocation_groups (associated_wallet);
create index associated_group_to_allocation on accounting.wallet_allocations_v2 (associated_allocation_group);