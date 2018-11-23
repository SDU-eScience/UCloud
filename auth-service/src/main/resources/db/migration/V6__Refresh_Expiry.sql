set search_path to auth;

alter table refresh_tokens
  add column refresh_token_expiry bigint default null;

