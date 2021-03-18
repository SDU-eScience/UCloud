alter table wallets drop column if exists allocated;
alter table wallets add column allocated bigint not null default 0;
alter table wallets drop column if exists used;
alter table wallets add column used bigint not null default 0;

create or replace function accounting.migrate_used() RETURNS void as
$$
declare
    w accounting.wallets;
    amount_used bigint;
begin
    for w in (select * from accounting.wallets) loop
            amount_used := 0;

            select coalesce(sum(amount), 0) into amount_used
            from transactions
            where
                    account_id = w.account_id and
                    account_type = w.account_type and
                    product_category = w.product_category and
                    product_provider = w.product_provider;

            update accounting.wallets
            set
                used = amount_used,
                allocated = amount_used + w.balance
            where
                    account_id = w.account_id and
                    account_type = w.account_type and
                    product_category = w.product_category and
                    product_provider = w.product_provider;
        end loop;
end
$$ LANGUAGE plpgsql;

select accounting.migrate_used();
drop function accounting.migrate_used();

create or replace function update_used() returns trigger as $$
begin
    update accounting.wallets
    set
        used = used + new.amount,

        -- update_allocated also triggers on a change to the balance, this will make sure that the value of
        -- allocated is still correct
        allocated = allocated + new.amount
    where
            account_id = new.account_id and
            account_type = new.account_type and
            product_category = new.product_category and
            product_provider = new.product_provider;
    return null;
end;
$$ language plpgsql;

drop trigger if exists update_used on accounting.transactions;
create trigger update_used after insert on transactions for each row execute procedure update_used();

create or replace function update_allocated() returns trigger as $$
declare
    change_in_balance bigint;
begin
    change_in_balance := new.balance - old.balance;

    update accounting.wallets
    set
        allocated = allocated + change_in_balance
    where
            account_id = new.account_id and
            account_type = new.account_type and
            product_provider = new.product_provider and
            product_category = new.product_category;
    return null;
end;
$$ language plpgsql;

drop trigger if exists update_allocated on accounting.wallets;
create trigger update_allocated after update of balance on wallets for each row execute procedure update_allocated();

create or replace function initialize_allocated() returns trigger as $$
begin
    update accounting.wallets
    set allocated = new.balance
    where
            account_id = new.account_id and
            account_type = new.account_type and
            product_provider = new.product_provider and
            product_category = new.product_category;
    return null;
end;
$$ language plpgsql;

drop trigger if exists initialize_allocated on accounting.wallets;
create trigger initialize_allocated after insert on accounting.wallets for each row execute procedure initialize_allocated();
