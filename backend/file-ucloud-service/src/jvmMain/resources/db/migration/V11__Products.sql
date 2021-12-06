with
    user_quotas as (
        select (regexp_split_to_array(path, '/'))[3] as username, ceil(quota_in_bytes / (1024 * 1024 * 1024)) as quota
        from storage.quotas
        where path like '/home/%'
    ),
    updates as (
        select wa.id as alloc_id, wa.balance, q.quota as new_quota, q.username
        from
            accounting.product_categories pc join
            accounting.wallets w on pc.id = w.category join
            accounting.wallet_allocations wa on wa.associated_wallet = w.id join
            accounting.wallet_owner wo on w.owned_by = wo.id join
            user_quotas q on wo.username = q.username
        where
            pc.product_type = 'STORAGE'
    )
update accounting.wallet_allocations wa
set balance = w.new_quota
from updates w
where wa.id = w.alloc_id;

drop trigger if exists require_immutable_product_category on accounting.product_categories;

update accounting.product_categories pc
set
    charge_type = 'DIFFERENTIAL_QUOTA',
    unit_of_price = 'PER_UNIT'
where
    pc.product_type = 'STORAGE';

update accounting.products p
set price_per_unit = 1
from accounting.product_categories pc
where
    pc.id = p.category and
    pc.product_type = 'STORAGE';

create trigger require_immutable_product_category
after update of charge_type, product_type on accounting.product_categories
for each row execute procedure accounting.require_immutable_product_category();
