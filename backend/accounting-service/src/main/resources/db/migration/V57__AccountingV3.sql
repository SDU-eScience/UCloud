--ALLOCATION UPDATES
alter table accounting.wallet_allocations add column retired bool default false;
alter table accounting.wallet_allocations add column retired_usage bigint default 0;

--WALLET UPDATES

--INTERNAL GROUPS PERSISTENCE???