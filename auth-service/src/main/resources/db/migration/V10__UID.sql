set search_path to auth;

alter table principals add column uid bigserial not null;
