alter table job_information add column reserved_price_per_unit bigint not null default 0;
alter table job_information add column reserved_provider text not null default 'ucloud';
alter table job_information add column reserved_category text not null default 'standard';
