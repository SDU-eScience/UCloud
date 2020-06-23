create sequence id_sequence start 1 increment 1;

create table news
(
    id          bigint       not null,
    title       varchar(255) not null,
    subtitle    varchar(255) not null,
    body        text         not null,
    posted_by   varchar(255) not null,
    show_from   timestamp    not null,
    hide_from   timestamp,
    hidden      boolean      not null,
    category    varchar(255) not null,

    primary key (id)
);
