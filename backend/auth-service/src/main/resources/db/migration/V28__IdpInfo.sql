drop table if exists auth.idp_auth_responses;

create table auth.idp_auth_responses(
    associated_user int not null references auth.principals(uid),
    idp int not null references auth.identity_providers(id),
    idp_identity text not null,
    first_names text default null,
    last_name text default null,
    organization_id text default null,
    email text default null,
    created_at timestamptz default now()
);

create index on auth.idp_auth_responses (associated_user, idp, idp_identity);
