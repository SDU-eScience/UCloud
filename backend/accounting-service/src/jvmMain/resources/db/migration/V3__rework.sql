create table balance
(
    account_id           text,
    account_type         text,
    account_machine_type text,
    balance              bigint,
    primary key (account_id, account_type, account_machine_type)
);

create table transactions
(
    account_id           text      not null,
    account_type         text      not null,
    account_machine_type text      not null,
    amount               bigint    not null,
    reservation_id       text      not null,
    is_reserved          bool      not null,
    initiated_by         text      not null,
    completed_at         timestamp not null,
    expires_at           timestamp,
    primary key (reservation_id),
    foreign key (account_id, account_machine_type, account_type) references balance (account_id, account_machine_type, account_type)
);

create table grant_administrators
(
    username text not null,
    primary key (username)
);
