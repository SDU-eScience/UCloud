with
    user_quotas as (
        select
            (regexp_split_to_array(path, '/'))[3] as project_id,
            ceil(quota_in_bytes / (1024 * 1024 * 1024)) as quota
        from storage.quotas
        where path like '/projects/%'
    ),
    updates as (
        select wa.id as alloc_id, wa.balance, q.quota as new_quota, q.project_id
        from
            accounting.product_categories pc join
            accounting.wallets w on pc.id = w.category join
            accounting.wallet_allocations wa on wa.associated_wallet = w.id join
            accounting.wallet_owner wo on w.owned_by = wo.id join
            user_quotas q on wo.project_id = q.project_id
        where
            pc.product_type = 'STORAGE'
    )
update accounting.wallet_allocations wa
set balance = w.new_quota
from updates w
where wa.id = w.alloc_id;

update accounting.wallet_allocations wa
set initial_balance = balance
where initial_balance > balance;

update accounting.wallet_allocations wa
set local_balance = balance
where local_balance > balance;
