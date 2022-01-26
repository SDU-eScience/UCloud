alter table accounting.wallet_allocations add column provider_generated_id text default null;
create unique index on accounting.wallet_allocations(provider_generated_id);
