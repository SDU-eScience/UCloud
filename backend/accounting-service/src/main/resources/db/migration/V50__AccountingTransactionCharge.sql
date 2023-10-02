alter table accounting.transaction_history add column local_change bigint default 0;
alter table accounting.transaction_history add column tree_change bigint default 0;
alter table accounting.transaction_history add column quota_change bigint default 0;
