create table quota_allocation(
    from_directory text   not null,
    to_directory   text   not null,
    allocation     bigint not null,
    primary key (from_directory, to_directory)
);