set search_path to auth;

alter table refresh_tokens
    add column extended_by varchar(255) default null;

alter table refresh_tokens
    add column scopes jsonb default '["all:write"]';

alter table refresh_tokens
    add column expires_after bigint not null default 600000;
