create type accounting.allocation_requests_group as enum (
    'ALL', 'PROJECT', 'PERSONAL'
);

alter table accounting.product_categories
    add column allow_allocation_requests_from accounting.allocation_requests_group not null default 'ALL'
;