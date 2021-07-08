insert into provider.resource (type, provider, created_by, project, product, provider_generated_id, created_at)
select 'provider', null, created_by, project, null, i.id, i.created_at
from provider.providers i;

alter table provider.providers add column resource bigint references provider.resource(id);

update provider.providers i
set resource = r.id
from provider.resource r
where r.provider_generated_id = i.id;

alter table provider.connected_with add column provider_id_new bigint;

update provider.connected_with
set provider_id_new = r.resource
from provider.providers r
where provider_id = r.id;

alter table provider.connected_with drop column provider_id;
alter table provider.connected_with rename column provider_id_new to provider_id;

alter table provider.resource drop constraint resource_provider_fkey;
alter table provider.providers drop column claim_token;
alter table provider.providers drop column created_by;
alter table provider.providers drop column project;
alter table provider.providers drop column created_at;
alter table provider.providers drop column acl;
alter table provider.providers rename column id to unique_name;
alter table provider.providers drop constraint providers_pkey;
alter table provider.providers add constraint providers_pkey primary key (resource);
create unique index unique_name_idx on provider.providers (unique_name);
alter table provider.connected_with add constraint connected_with_fkey foreign key (provider_id)
    references provider.providers (resource);
alter table provider.resource add constraint resource_provider_fkey foreign key (provider)
    references provider.providers(unique_name);

create or replace function provider.provider_to_json(
    provider_in provider.providers
) returns jsonb language sql as $$
    select jsonb_build_object(
        'specification', jsonb_build_object(
            'id', provider_in.unique_name,
            'domain', provider_in.domain,
            'https', provider_in.https,
            'port', provider_in.port
        ),
        'refreshToken', provider_in.refresh_token,
        'publicKey', provider_in.public_key
    );
$$;

create function provider.approve_request(
    shared_secret_in text,
    public_key_in text,
    private_key_in text,
    predefined_resource_id text
) returns provider.providers language plpgsql as $$
declare
    request provider.approval_request;
    project_id text;
    generated_refresh_token text;
    result provider.providers;
    resource_id bigint;
begin
    if predefined_resource_id is not null then
        resource_id = predefined_resource_id;
    end if;

    delete from provider.approval_request
    where
        signed_by is not null and
        approval_request.shared_secret = shared_secret_in and
        created_at >= now() - '5 minutes'::interval
    returning * into request;

    if request is null then
        raise exception 'Bad token provided';
    end if;

    if resource_id is null then
        insert into project.projects
        (id, created_at, modified_at, title, archived, parent, dmp, subprojects_renameable)
        values (
           gen_random_uuid()::text,
           now(),
           now(),
           'Provider: ' || request.requested_id,
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

        insert into provider.resource
            (type, provider, created_at, created_by, project, product, provider_generated_id, confirmed_by_provider)
        values
            ('provider', null, now(), request.signed_by, project_id, null, request.requested_id, true)
        returning id into resource_id;
    end if;

    select gen_random_uuid() into generated_refresh_token;

    insert into provider.providers
        (unique_name, domain, https, port, refresh_token, public_key, resource)
    values (
        request.requested_id,
        request.domain,
        request.https,
        request.port,
        generated_refresh_token,
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
$$;
