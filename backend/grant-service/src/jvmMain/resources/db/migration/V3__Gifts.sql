create sequence gift_id_sequence start 1;

create table gifts(
    id bigint default nextval('gift_id_sequence') primary key,
    resources_owned_by text not null,
    title text not null,
    description text not null
);

create table gifts_user_criteria(
    gift_id bigint not null references gifts(id),
    type text not null,
    applicant_id text not null,
    primary key (gift_id, type, applicant_id)
);

create table gift_resources(
    gift_id bigint not null references gifts(id),
    product_category text not null,
    product_provider text not null,
    credits bigint default null,
    quota bigint default null,
    primary key (gift_id, product_category, product_provider)
);

create table gifts_claimed(
    gift_id bigint not null references gifts(id),
    user_id text not null,
    primary key (gift_id, user_id)
);

create index gifts_claimed_user_id on gifts_claimed(user_id);
