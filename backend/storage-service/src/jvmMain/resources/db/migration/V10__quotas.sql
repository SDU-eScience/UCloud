create table quotas(
    path text not null,
    quota_in_bytes bigint not null,
    primary key (path)
);
