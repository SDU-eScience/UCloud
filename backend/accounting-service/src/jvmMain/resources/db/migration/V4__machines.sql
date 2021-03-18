create table machines(
    name text not null,
    type text not null,
    price_per_hour bigint not null,
    active bool not null,
    default_machine bool not null,
    cpu int,
    gpu int,
    memory_in_gigs int,

    primary key (name)
);