set search_path to notification;

alter table subscriptions add column last_ping timestamp not null default now();
