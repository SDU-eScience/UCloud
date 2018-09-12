set search_path to app;
alter table jobs add column jwt VARCHAR(256) not null default 'invalid-token';