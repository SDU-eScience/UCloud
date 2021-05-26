create table provider.approval_request(
    shared_secret text primary key,
    requested_id text not null unique,
    domain text not null,
    https bool not null,
    port int,
    signed_by text references auth.principals(id) default null,
    created_at timestamptz default now()
);

create extension if not exists "uuid-ossp";
create extension if not exists "pgcrypto";

create function provider.approve_request(
    shared_secret_in text,
    public_key_in text,
    private_key_in text
) returns provider.providers as $$
declare
    request provider.approval_request;
    project_id text;
    generated_refresh_token text;
    result provider.providers;
begin
    delete from provider.approval_request
    where
        signed_by is not null and
        approval_request.shared_secret = shared_secret_in and
        created_at >= now() - '5 minutes'::interval
    returning * into request;

    if request is null then
        raise exception 'Bad token provided';
    end if;

    insert into project.projects
    (id, created_at, modified_at, title, archived, parent, dmp, subprojects_renameable)
    values (
        gen_random_uuid()::text,
        now(),
        now(),
        'Project: ' || request.requested_id,
        false,
        null,
        null,
        false
    ) returning id into project_id;

    insert into project.project_members (created_at, modified_at, role, username, project_id)
    values (
        now(),
        now(),
        'PI',
        request.signed_by,
        project_id
    );

    select gen_random_uuid() into generated_refresh_token;

    insert into provider.providers (id, domain, https, port, created_by, project, refresh_token, claim_token, public_key)
    values (
        request.requested_id,
        request.domain,
        request.https,
        request.port,
        request.signed_by,
        project_id,
        generated_refresh_token,
        '',
        public_key_in
    ) returning * into result;

    insert into auth.providers(id, pub_key, priv_key, refresh_token, claim_token, did_claim)
    values (
        request.requested_id,
        public_key_in,
        private_key_in,
        generated_refresh_token,
        '',
        true
    );

    return result;
end;
$$ language plpgsql;
