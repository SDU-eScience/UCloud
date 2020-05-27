create table subscriptions(
    file_id text not null,
    subscriber text not null,
    primary key (file_id, subscriber)
);
