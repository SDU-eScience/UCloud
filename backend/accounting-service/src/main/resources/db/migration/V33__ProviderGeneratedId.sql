alter table provider.resource drop constraint if exists resource_provider_generated_id_key;
alter table provider.resource add constraint provider_generated_id_unique unique (provider, provider_generated_id);
