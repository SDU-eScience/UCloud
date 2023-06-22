alter table auth.registration add column identity_provider int not null default 1 references auth.identity_providers(id);
alter table auth.registration alter column identity_provider drop default;
alter table auth.registration rename column wayf_id to idp_identity;
