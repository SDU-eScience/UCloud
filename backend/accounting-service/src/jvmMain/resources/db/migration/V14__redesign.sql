create schema accounting2;

set search_path to accounting2;

create type product_type as enum ('COMPUTE', 'STORAGE', 'INGRESS', 'LICENSE', 'NETWORK_IP');
create type charge_type as enum ('ABSOLUTE', 'DIFFERENTIAL_QUOTA');

create table product_category(
    id bigserial primary key,
    name text not null,
    provider_id text references provider.providers(id),
    product_type product_type not null ,
    charge_type charge_type not null,
    unique (name, provider_id)
);

-- Migrate product categories from old data.
INSERT INTO product_category (name, provider_id, product_type, charge_type)
SELECT product_categories.category, product_categories.provider, product_categories.area, 'ABSOLUTE'
FROM accounting.product_categories;

-- Set Storage types to be differential.
UPDATE product_category
SET charge_type = 'DIFFERENTIAL_QUOTA'
WHERE product_type == 'STORAGE';

create type product_price_unit as enum ('PER_MINUTE', 'PER_HOUR', 'PER_DAY', 'PER_WEEK', 'PER_UNIT');

create table product(
    id bigserial primary key,
    product_category_id bigint not null references product_category(id),
    name text not null,
    price_per_unit bigint not null check (price_per_unit >= 0),
    unit_of_price product_price_unit not null,
    product_type product_type not null,
    description text,
    version bigint not null,
    priority bigint,
    cpu bigint,
    gpu bigint,
    memory_in_gigs bigint,
    license_tags jsonb,
    free_to_use boolean not null default false,
    -- TODO Metadata
    unique (name, version, product_category_id)
);
-- Migrate products from old data.
INSERT INTO accounting2.product (product_category_id, name, price_per_unit, unit_of_price, product_type, description, version, priority, cpu, gpu, memory_in_gigs, license_tags, completely_free_just_go_ahead_and_use_it)
SELECT accounting.products.category,
       accounting.products.id,
       accounting.products.price_per_unit,
       'PER_MINUTE',
       accounting.products.area,
       accounting.products.description,
       1,
       accounting.products.priority,
       accounting.products.cpu,
       accounting.products.gpu,
       accounting.products.memory_in_gigs,
       accounting.products.license_tags,
       false
FROM accounting.products;

-- set free on licenses
UPDATE accounting2.product
SET free_to_use = true
WHERE product_type == 'LICENSE';
-- set unit/activation cost on Ingress and Network_ips
UPDATE accounting2.product
SET unit_of_price = 'PER_UNIT'
WHERE product_type == 'INGRESS' OR product_type == 'NETWORK_IP';

create type allocation_selector_policy as enum ('ORDERED', 'EXPIRE_FIRST');

create table wallet_owner(
     id bigserial primary key,
     username text references auth.principals(id),
     project_id text references project.projects(id)
    -- Exactly one needs to be not null
    constraint chk_only_one check (
        (username is not null and project_id is null) or (username is null and project_id is not null)
    )
);

-- Migrate wallet owners from old data.
INSERT INTO wallet_owner (username)
SELECT id FROM auth.principals
WHERE role = 'USER';

INSERT INTO wallet_owner (project_id)
SELECT id FROM project.projects;

create table wallet(
    id bigserial primary key,
    owner_id bigint references wallet_owner(id),
    allocation_selector_policy allocation_selector_policy not null,
    category_id bigint not null references product_category(id),
    unique (owner_id, category_id)
);

create table wallet_allocation(
    balance bigint not null,
    initial_balance bigint not null,
    start_date timestamp not null,
    end_date timestamp,
    parent_wallet_id bigint references wallet(id),
    transaction_id bigint not null references transaction(id) -- TODO Check that this is charge or deposit (seems to must be done in backend)
);

create type transaction_type as enum ('TRANSFER', 'CHARGE', 'DEPOSIT');

create table transaction(
    id bigserial primary key,
    transaction_type transaction_type not null,
    target_wallet_id bigint not null references wallet(id),
    units bigint not null,
    number_of_products bigint not null,
    action_performed_by text references auth.principals(id),
    action_performed_by_wallet bigint references wallet(id),
    product_id bigint references product(id),
    transfer_from_wallet_id bigint references wallet(id),
    description text not null,
    -- Change in wallet_allocation: product.price_per_unit * units * number_of_products
    constraint check_deposit_convention check (
                transaction_type != 'DEPOSIT' or number_of_products = 1
        ),
    constraint check_target check(
                transaction_type != 'TRANSFER' or transfer_from_wallet_id is not null
        ),
    constraint root_deposits check (
                transaction_type != 'DEPOSIT' and action_performed_by_wallet is not null
        )
    -- TODO enforce that product id matches wallet (also check on backend)
);

--TODO migrate in next script (need to find oldest ancestor as giver)

create type product_category_relationship_type as enum ('STORAGE_CREDITS', 'NODE_HOURS');

create table product_category_relationship(
    type product_category_relationship_type not null,
    credits_category bigint references product_category(id),
    quota_category bigint references product_category(id),
    hours_category bigint references product_category(id),
    constraint storage_credits check(
              type != 'STORAGE_CREDITS' or (
              credits_category is not null and
              quota_category is not null
          )
      ),
    constraint node_hours check(
              type != 'NODE_HOURS' or (
              credits_category is not null and
              hours_category is not null
          )
      )
);
