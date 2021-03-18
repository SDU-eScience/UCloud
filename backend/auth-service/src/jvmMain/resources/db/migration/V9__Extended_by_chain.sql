set search_path to auth;

alter table refresh_tokens
  add column extended_by_chain jsonb default '[]';
