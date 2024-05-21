with new_wallets as (
    insert into accounting.wallets_v2 ( wallet_owner, product_category, local_usage, local_retired_usage, excess_usage, total_allocated, total_retired_allocated, was_locked, last_significant_update_at)
        select wallet_owner, product_category, local_usage, local_retired_usage, excess_usage, total_allocated, total_retired_allocated, was_locked, last_significant_update_at
        from accounting.wallets_v2
        where id = 0
        returning id, 0 as old_id
),
     update_associated_wallets as (
         update accounting.allocation_groups
             set associated_wallet = newwal.id
             from
                 new_wallets newwal
             where associated_wallet = newwal.old_id
     ),
     update_parent_wallets as (
         update accounting.allocation_groups
             set parent_wallet = newwal.id
             from new_wallets newwal
             where parent_wallet = newwal.old_id
     ),
     allocInsert as (
         insert into accounting.wallet_allocations_v2 ( associated_allocation_group, granted_in, quota, allocation_start_time, allocation_end_time, retired, retired_usage)
             select associated_allocation_group, granted_in, quota, allocation_start_time, allocation_end_time, retired, retired_usage
             from accounting.wallet_allocations_v2
             where id = 0
     ),
     updateSamles as (
         update accounting.wallet_samples_v2
             set wallet_id = newwal.id
             from new_wallets newwal
             where wallet_id = newwal.old_id
     ),
     delete_alloc as (
         delete from accounting.wallet_allocations_v2 where id = 0
     )
delete from accounting.wallets_v2 where id = 0;