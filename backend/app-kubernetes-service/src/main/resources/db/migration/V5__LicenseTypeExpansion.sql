alter table app_kubernetes.license_servers
add column price_per_unit bigint default 1000000 not null,
add column description text default '' not null,
add column product_availability text default '' null,
add column priority int default 0 not null,
add column payment_model text default 'PER_ACTIVATION' not null;