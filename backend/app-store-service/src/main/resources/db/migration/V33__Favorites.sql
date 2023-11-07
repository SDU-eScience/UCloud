alter table app_store.favorited_by
    drop constraint fkdr4vaq58mgr0nkk0xoq41ljgo;

create or replace function require_favorite_app_exists() returns trigger
    language plpgsql
as $$
    declare
        found integer;
    begin
        select count(*) into found from app_store.applications where name = new.application_name;

        if (found < 1) then
            raise exception 'Application with name does not exist';
        end if;
        return null;
    end
$$;

create or replace trigger require_favorite_app_exists
    after insert or update on app_store.favorited_by
    for each row execute procedure app_store.require_favorite_app_exists();

alter table app_store.application_groups
    drop constraint application_groups_default_name_default_version_fkey;

create or replace function require_default_app_exists() returns trigger
    language plpgsql
as $$
    declare
        found integer;
    begin
        select count(*) into found from app_store.applications where name = new.default_version;

        if (found < 1 and new.default_version is not null) then
            raise exception 'Application with name does not exist';
        end if;
        return null;
    end
$$;

create or replace trigger require_default_app_exists
    after insert or update on app_store.application_groups
    for each row execute function require_default_app_exists();

alter table app_store.favorited_by drop column application_version;
alter table app_store.application_groups drop column default_version;

alter table app_store.favorited_by drop column id;
alter table app_store.favorited_by add primary key (the_user, application_name);