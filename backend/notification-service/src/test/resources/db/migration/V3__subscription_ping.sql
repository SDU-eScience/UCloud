
alter table notification.subscriptions add column last_ping timestamp not null default now();
