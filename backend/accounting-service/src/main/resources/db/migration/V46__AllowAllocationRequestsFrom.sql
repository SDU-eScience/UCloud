create type accounting.allow_accounting_requests_from as enum (
    'ALL', 'PROJECTS', 'PERSONAL'
);

alter table accounting.product_categories
    add column allow_accounting_requests_from accounting.allow_accounting_requests_from not null default 'ALL'
;