alter table app_store.spotlight_items drop constraint spotlight_items_pkey;
create unique index on app_store.spotlight_items (spotlight_id, priority);
