set search_path to app;
alter table jobs add column jwt VARCHAR(2048) not null default 'invalid-token';