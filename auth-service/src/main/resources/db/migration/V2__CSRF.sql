-- Introduces a CSRF token associated with refresh-tokens

set search_path to auth;

delete from refresh_tokens; -- We don't currently need to preserve these, just throw them out.
alter table refresh_tokens add column csrf varchar(256) not null default '';
