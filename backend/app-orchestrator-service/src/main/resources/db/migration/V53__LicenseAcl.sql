alter table licenses add column acl jsonb default '[]'::jsonb;
