alter table accounting.wallet_allocations add column if not exists can_allocate bool not null default true;
alter table accounting.wallet_allocations add column if not exists allow_sub_allocations_to_allocate bool not null default true;

update accounting.wallet_allocations alloc
set can_allocate = false
from
    accounting.wallets w join
    accounting.wallet_owner wo on w.owned_by = wo.id
where
    alloc.associated_wallet = w.id and
    wo.username is not null;
