-- TODO This is definitely not done. As a result, I am adding an error here such that it cannot run
this is an error;

drop table if exists auth.idp_connections;
drop table if exists auth.identity_providers;

create table auth.identity_providers(
    id serial4 primary key,
    title text unique not null,
    configuration jsonb not null
);

insert into auth.identity_providers (title, configuration)
values ('wayf', '{ "type": "wayf" }'::jsonb);

create table auth.idp_connections(
    principal int references auth.principals(uid),
    idp int references auth.identity_providers(id),
    provider_identity text not null,

    primary key (provider_identity, idp)
);

insert into auth.idp_connections (principal, idp, provider_identity)
select p.uid, idp.id, p.wayf_id
from
    auth.principals p,
    auth.identity_providers idp
where
    p.wayf_id is not null
    and idp.title = 'wayf';

alter table auth.principals drop column if exists orc_id;
alter table auth.principals drop column if exists title;
alter table auth.principals drop column if exists phone_number;
alter table auth.principals drop column if exists wayf_id;

alter table auth.principals alter column created_at set default now();
alter table auth.principals alter column modified_at set default now();
alter table auth.principals alter column first_names set default null;
alter table auth.principals alter column last_name set default null;
alter table auth.principals alter column email set default null;
alter table auth.principals alter column org_id set default null;
alter table auth.principals alter column hashed_password set default null;
alter table auth.principals alter column salt set default null;

update auth.principals
set dtype = 'PERSON'
where
    dtype = 'WAYF'
    or dtype = 'PASS