create table if not exists accounting.predictions (
    wallet_id bigint not null,
    days_in_future int not null,
    prediction real not null
)