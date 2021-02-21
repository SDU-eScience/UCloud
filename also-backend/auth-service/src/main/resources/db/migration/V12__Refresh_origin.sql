alter table refresh_tokens add column created_at timestamp;
alter table refresh_tokens add column ip varchar(255);
alter table refresh_tokens add column user_agent varchar(4096);
