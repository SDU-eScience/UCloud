drop trigger if exists allocation_path_check on accounting.wallet_allocations;
drop trigger if exists allocation_date_check_insert on accounting.wallet_allocations;

drop function if exists accounting.transaction_to_json(transaction_in accounting.transactions, product_in accounting.products, category_in accounting.product_categories);
drop function if exists accounting.transfer(requests accounting.transfer_request[]);
drop function if exists accounting.process_charge_requests(requests accounting.charge_request[]);
drop function if exists accounting.process_transfer_requests(requests accounting.transfer_request[]);
drop function if exists accounting.allocation_date_check();
drop function if exists accounting.allocation_path_check();
drop function if exists accounting.deposit(requests accounting.deposit_request[]);
drop function if exists accounting.recreate_product_price_unit(categoryname text, type accounting.product_type);
drop function if exists accounting.update_allocations(request accounting.allocation_update_request[]);
drop function if exists accounting.credit_check(requests accounting.charge_request[]);
drop function if exists accounting.credit_check(requests accounting.transfer_request[]);
drop function if exists accounting.wallet_allocation_to_json(allocation_in accounting.wallet_allocations);
drop function if exists accounting.wallet_to_json(wallet_in accounting.wallets, owner_in accounting.wallet_owner, allocations_in accounting.wallet_allocations[], category_in accounting.product_categories);
drop function if exists accounting.charge(requests accounting.charge_request[]);

drop table if exists accounting.product_category_relationship;

drop table if exists accounting.transaction_history;
drop table if exists accounting.transactions;
drop table if exists accounting.transactions_backup;
drop table if exists accounting.wallet_allocations;
drop table if exists accounting.wallets;

drop type if exists accounting.allocation_selector_policy;
drop type if exists accounting.transaction_type;
drop type if exists accounting.product_category_relationship_type;
drop type if exists accounting.charge_request;
drop type if exists accounting.deposit_request;
drop type if exists accounting.allocation_update_request;
drop type if exists accounting.transfer_request;


create table if not exists accounting.scoped_usage(
    key text primary key,
    usage int8 not null
);
