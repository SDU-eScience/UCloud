alter table app_store.favorited_by
    drop constraint if exists fkdr4vaq58mgr0nkk0xoq41ljgo;

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
    drop constraint if exists application_groups_default_name_default_version_fkey;

create or replace function require_default_app_exists() returns trigger
    language plpgsql
as $$
    declare
        found integer;
    begin
        select count(*) into found from app_store.applications where name = new.default_name;

        if (found < 1 and new.default_name is not null) then
            raise exception 'Application with name does not exist';
        end if;
        return null;
    end
$$;

create or replace trigger require_default_app_exists
    after insert or update on app_store.application_groups
    for each row execute function require_default_app_exists();

create table app_store.favorited_by2
(
	id bigint not null primary key,
	the_user varchar(255),
	application_name varchar(255),
	application_version varchar(255),
	rn int
);

insert into app_store.favorited_by2(id, the_user, application_name, application_version, rn)
select id, the_user, application_name, application_version, row_number() over (partition by application_name, the_user)
from app_store.favorited_by;

delete from app_store.favorited_by2 where rn > 1;
alter table app_store.favorited_by2 drop column rn;

drop table app_store.favorited_by;
alter table app_store.favorited_by2 rename to favorited_by;

alter table app_store.favorited_by drop column application_version;
alter table app_store.application_groups drop column default_version;

alter table app_store.favorited_by drop column id;
alter table app_store.favorited_by add primary key (the_user, application_name);