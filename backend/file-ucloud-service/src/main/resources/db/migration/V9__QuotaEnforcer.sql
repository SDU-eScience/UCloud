create table file_ucloud.quota_locked(
    collection bigint primary key,
    scan_id text
);

create index on file_ucloud.quota_locked(scan_id);
