-- Migrate license balances
with wallets as (
    select w.id
    from
        accounting.wallets w join
        accounting.product_categories pc on pc.id = w.category
    where
        pc.product_type = 'LICENSE'
)
update accounting.wallet_allocations wa
set
    balance = balance / 1000000,
    initial_balance = initial_balance / 1000000,
    local_balance = local_balance / 1000000
from wallets w
where wa.associated_wallet = w.id and balance > 1000000;

with wallets as (
    select w.id
    from
        accounting.wallets w join
        accounting.product_categories pc on pc.id = w.category
    where
        pc.product_type = 'NETWORK_IP'
)
update accounting.wallet_allocations wa
set
    balance = balance / 1000000,
    initial_balance = initial_balance / 1000000,
    local_balance = local_balance / 1000000
from wallets w
where wa.associated_wallet = w.id and balance > 1000000;
