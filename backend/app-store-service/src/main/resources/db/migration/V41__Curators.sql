drop index app_store.unique_public;
drop index app_store.unique_private;
alter table app_store.curators drop column public_read;
alter table app_store.curators drop column can_create_categories;
alter table app_store.curators add column mandated_prefix text not null default '';
