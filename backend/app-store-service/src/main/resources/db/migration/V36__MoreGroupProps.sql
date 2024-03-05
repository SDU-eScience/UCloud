alter table app_store.application_groups add column if not exists logo_has_text bool default false;
alter table app_store.application_groups add column if not exists background_color text default null;
