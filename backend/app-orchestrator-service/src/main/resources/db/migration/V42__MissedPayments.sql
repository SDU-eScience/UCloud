create table missed_payments(
    reservation_id text not null,
    amount bigint not null,
    created_at timestamp not null,
    primary key (reservation_id)
);
