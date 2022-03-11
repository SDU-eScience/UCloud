create unique index on provider.connected_with (username, provider_id);
alter table provider.connected_with add column expires_at timestamptz default null;
