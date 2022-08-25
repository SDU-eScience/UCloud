create or replace function accounting.initialize_allocated() returns trigger
	language plpgsql
as $$
begin
    update accounting.wallets
    set allocated = new.balance
    where
            account_id = new.account_id and
            account_type = new.account_type and
            category = new.category;
    return null;
end;
$$;

create or replace function accounting.update_allocated() returns trigger
	language plpgsql
as $$
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
            category = new.category;
    return null;
end;
$$;

create or replace function accounting.update_used() returns trigger
	language plpgsql
as $$
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
            category = new.category;
    return null;
end;
$$;
