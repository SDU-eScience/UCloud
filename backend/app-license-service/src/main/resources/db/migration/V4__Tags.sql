create table tags
(
    name           varchar(255) not null,
    license_server varchar(255) not null,
    primary key (name,license_server)
);

