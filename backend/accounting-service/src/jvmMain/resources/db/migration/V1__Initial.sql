create schema if not exists accounting;
create schema if not exists "grant";
create schema if not exists project;
create schema if not exists "provider";

create extension if not exists "uuid-ossp";
create extension if not exists "pgcrypto";

-- old accounting-service
create table if not exists accounting.job_completed_events
(
	job_id varchar(255) not null
		constraint job_completed_events_pkey
			primary key,
	application_name varchar(255),
	application_version varchar(255),
	duration_in_ms bigint not null,
	nodes integer not null,
	started_by varchar(255),
	timestamp timestamp,
	machine_reservation_cpu integer,
	machine_reservation_gpu integer,
	machine_reservation_mem integer,
	machine_reservation_name text,
	project_id text
);

create table if not exists accounting.product_categories
(
	provider text not null,
	category text not null,
	area text not null,
	constraint product_categories_pkey
		primary key (provider, category)
);

create unique index if not exists product_categories_id
	on accounting.product_categories (upper(provider), upper(category));

create table if not exists accounting.products
(
	provider text not null,
	category text not null,
	area text not null,
	id text not null,
	price_per_unit bigint not null,
	description text default ''::text not null,
	availability text,
	priority integer default 0,
	cpu integer,
	gpu integer,
	memory_in_gigs integer,
	license_tags jsonb,
	payment_model text,
	hidden_in_grant_applications boolean default false not null,
	constraint products_pkey
		primary key (id, provider, category),
	constraint products_provider_category_fkey
		foreign key (provider, category) references accounting.product_categories
);

create unique index if not exists products_id
	on accounting.products (upper(id), upper(provider), upper(category), upper(area));

create table if not exists accounting.wallets
(
	account_id text not null,
	account_type text not null,
	product_category text not null,
	product_provider text not null,
	balance bigint default 0 not null,
	low_funds_notifications_send boolean default false not null,
	allocated bigint default 0 not null,
	used bigint default 0 not null,
	constraint wallets_pkey
		primary key (account_id, account_type, product_category, product_provider),
	constraint wallets_product_provider_product_category_fkey
		foreign key (product_provider, product_category) references accounting.product_categories
);

create table if not exists accounting.transactions
(
	account_id text not null,
	account_type text not null,
	product_category text not null,
	product_provider text not null,
	id text not null,
	product_id text not null,
	units bigint not null,
	amount bigint not null,
	is_reserved boolean default false,
	initiated_by text not null,
	completed_at timestamp not null,
	original_account_id text not null,
	expires_at timestamp,
	transaction_comment varchar(255),
	constraint transactions_pkey
		primary key (id, account_id, account_type),
	constraint transactions_product_provider_product_category_fkey
		foreign key (product_provider, product_category) references accounting.product_categories,
	constraint transactions_account_id_account_type_product_category_prod_fkey
		foreign key (account_id, account_type, product_category, product_provider) references accounting.wallets
);

create or replace function accounting.update_used() returns trigger
	language plpgsql
as $$
begin
    update accounting.wallets
    set
        used = used + new.amount,

        -- update_allocated also triggers on a change to the balance, this will make sure that the value of
        -- allocated is still correct
        allocated = allocated + new.amount
    where
            account_id = new.account_id and
            account_type = new.account_type and
            product_category = new.product_category and
            product_provider = new.product_provider;
    return null;
end;
$$;

drop trigger if exists update_used on accounting.transactions;
create trigger update_used
	after insert
	on accounting.transactions
	for each row
	execute procedure accounting.update_used();

create or replace function accounting.update_allocated() returns trigger
	language plpgsql
as $$
declare
    change_in_balance bigint;
begin
    change_in_balance := new.balance - old.balance;

    update accounting.wallets
    set
        allocated = allocated + change_in_balance
    where
            account_id = new.account_id and
            account_type = new.account_type and
            product_provider = new.product_provider and
            product_category = new.product_category;
    return null;
end;
$$;

drop trigger if exists update_allocated on accounting.wallets;
create trigger update_allocated
	after update
	of balance
	on accounting.wallets
	for each row
	execute procedure accounting.update_allocated();

create or replace function accounting.initialize_allocated() returns trigger
	language plpgsql
as $$
begin
    update accounting.wallets
    set allocated = new.balance
    where
            account_id = new.account_id and
            account_type = new.account_type and
            product_provider = new.product_provider and
            product_category = new.product_category;
    return null;
end;
$$;

drop trigger if exists initialize_allocated on accounting.wallets;
create trigger initialize_allocated
	after insert
	on accounting.wallets
	for each row
	execute procedure accounting.initialize_allocated();

-- Old project-service
create sequence if not exists project.hibernate_sequence;

create table if not exists project.projects
(
	id varchar(255) not null
		constraint projects_pkey
			primary key,
	created_at timestamp,
	modified_at timestamp,
	title varchar(4096),
	archived boolean default false,
	parent text
		constraint projects_parent_fkey
			references project.projects,
	dmp text,
	subprojects_renameable boolean default false,
	constraint ensure_root_title_unique
		exclude (title with =)
);

create table if not exists project.project_members
(
	created_at timestamp,
	modified_at timestamp,
	role varchar(255),
	username varchar(255) not null,
	project_id varchar(255) not null
		constraint project_members_project_id_fkey
			references project.projects
				on delete cascade,
	constraint project_members_pkey
		primary key (username, project_id)
);

create unique index if not exists project_id_case
	on project.projects (upper(id::text));

create unique index if not exists project_path_unique
	on project.projects (parent, upper(title::text));

create table if not exists project.groups
(
	title varchar(255) not null,
	project varchar(255) not null
		constraint groups_project_fkey
			references project.projects,
	id varchar(255) default (uuid_in((md5(((random())::text || (clock_timestamp())::text)))::cstring))::text not null
		constraint groups_pkey
			primary key,
	constraint groups_id_project_key
		unique (id, project)
);

create unique index if not exists group_title_uniq
	on project.groups (lower(title::text), project);

create table if not exists project.group_members
(
	username varchar(2048) not null,
	group_id varchar(255) not null
		constraint group_members_group_fkey
			references project.groups,
	constraint group_members_pkey
		primary key (group_id, username)
);

create table if not exists project.project_membership_verification
(
	project_id text not null
		constraint project_membership_verification_project_id_fkey
			references project.projects,
	verification timestamp not null,
	verified_by text,
	constraint project_membership_verification_pkey
		primary key (project_id, verification)
);

create table if not exists project.cooldowns
(
	username text not null,
	project text not null,
	timestamp timestamp not null,
	constraint cooldowns_pkey
		primary key (username, project, timestamp)
);

create table if not exists project.project_favorite
(
	project_id text not null
		constraint project_favorite_project_id_fkey
			references project.projects
				on delete cascade,
	username text not null,
	constraint project_favorite_pkey
		primary key (project_id, username),
	constraint project_favorite_project_members_id_fkey
		foreign key (project_id, username) references project.project_members (project_id, username)
			on delete cascade
);

create table if not exists project.invites
(
	project_id text not null
		constraint invites_project_id_fkey
			references project.projects
				on delete cascade,
	username text not null,
	invited_by text not null,
	created_at timestamp default now(),
	constraint invites_pkey
		primary key (project_id, username)
);

create or replace function project.check_group_acl(uname text, is_admin boolean, group_filter text) returns boolean
	language plpgsql
as $$
DECLARE passed BOOLEAN;
BEGIN
    select (
        is_admin or
        uname in (
            select gm.username
            from project.group_members gm
            where
                gm.username = uname and
                gm.group_id = group_filter
        )
    ) into passed;

    RETURN passed;
END;
$$;

create or replace function project.is_favorite(uname text, project text) returns boolean
	language plpgsql
as $$
declare
    is_favorite boolean;
begin
    select count(*) > 0
    into is_favorite
    from project.project_favorite fav
    where fav.username = uname
      and fav.project_id = project;

    return is_favorite;
end;
$$;

-- Old provider-service
drop table if exists provider.resource_acl_entry;
drop function if exists provider.update_acl(actor_username_in text, resource_id_in text, resource_type_in text, resource_provider_in text, entities_to_add_in text[], entities_to_remove_in text[]);
drop type if exists provider.acl_entity_type;
create type provider.acl_entity_type as enum ('project_group', 'user');

create table if not exists provider.providers
(
	id text not null
		constraint providers_pkey
			primary key,
	domain text not null,
	https boolean not null,
	port integer not null,
	created_by text not null,
	project text not null,
	refresh_token text,
	created_at timestamp default now() not null,
	acl jsonb default '[]'::jsonb not null,
	claim_token text,
	public_key text
);

create table if not exists provider.resource
(
	id text not null,
	type text not null,
	provider text not null,
	constraint resource_pkey
		primary key (id, type, provider)
);

create table if not exists provider.resource_acl_entry
(
	entity_type provider.acl_entity_type,
	project_id text,
	group_id text,
	username text
		constraint resource_acl_entry_username_fkey
			references auth.principals,
	resource_id text not null,
	resource_type text not null,
	resource_provider text not null,
	permission text not null,
	constraint resource_acl_entry_project_id_group_id_fkey
		foreign key (project_id, group_id) references project.groups (project, id),
	constraint resource_acl_entry_resource_id_resource_type_resource_prov_fkey
		foreign key (resource_id, resource_type, resource_provider) references provider.resource,
	constraint entity_constraint
		check ((username IS NOT NULL) OR ((project_id IS NOT NULL) AND (group_id IS NOT NULL))),
	constraint only_one_entity
		check (((username IS NULL) AND (project_id IS NOT NULL)) OR ((username IS NOT NULL) AND (project_id IS NULL))),
	constraint type_matches_entity
		check (((entity_type = 'project_group'::provider.acl_entity_type) AND (project_id IS NOT NULL)) OR ((entity_type = 'user'::provider.acl_entity_type) AND (username IS NOT NULL)))
);

create unique index if not exists acl_entry_unique
	on provider.resource_acl_entry (entity_type, COALESCE(project_id, ''::text), COALESCE(group_id, ''::text), COALESCE(username, ''::text), resource_id, resource_type, resource_provider);

create table if not exists provider.connected_with
(
	username text not null
		constraint connected_with_username_fkey
			references auth.principals,
	provider_id text not null
		constraint connected_with_provider_id_fkey
			references provider.providers,
	constraint connected_with_pkey
		primary key (username, provider_id)
);

create table if not exists provider.approval_request
(
	shared_secret text not null
		constraint approval_request_pkey
			primary key,
	requested_id text not null
		constraint approval_request_requested_id_key
			unique,
	domain text not null,
	https boolean not null,
	port integer,
	signed_by text
		constraint approval_request_signed_by_fkey
			references auth.principals,
	created_at timestamp with time zone default now()
);

create or replace function provider.accessible_resources(username_in text, resource_type_in text) returns TABLE(id text, permission text, provider text)
	language plpgsql
as $$
 begin
    return query
        select distinct
            r.id,
            case(pm.role)
                when 'PI' then 'ADMIN'
                when 'ADMIN' then 'ADMIN'
                else acl.permission
            end as permission,
            r.provider
        from
            provider.resource r join
            provider.resource_acl_entry acl on
                r.id = acl.resource_id and
                r.type = acl.resource_type and
                r.provider = acl.resource_provider left join
            project.project_members pm on acl.project_id = pm.project_id and pm.username = username_in left join
            project.groups g on pm.project_id = g.project and acl.group_id = g.id left join
            project.group_members gm on g.id = gm.group_id and gm.username = username_in
        where
            r.type = resource_type_in  and
            (
                (acl.username = username_in) or
                (pm.role = 'PI' or pm.role = 'ADMIN') or
                (gm.username is not null)
           );
end
$$;

create or replace function provider.update_acl(actor_username_in text, resource_id_in text, resource_type_in text, resource_provider_in text, entities_to_add_in text[], entities_to_remove_in text[]) returns boolean
	language plpgsql
as $$
declare
    has_permission bool;
begin
    select exists(
        select 1
        from provider.accessible_resources(actor_username_in, resource_type_in)
        where id = resource_id_in and permission = 'ADMIN' and provider = resource_provider_in
    ) into has_permission;

    if not has_permission then
        return false;
    end if;

    insert into provider.resource_acl_entry
    (entity_type, project_id, group_id, username, resource_id, resource_type, resource_provider, permission)
    values (
        unnest(entities_to_add_in[0]::provider.acl_entity_type[]),
        unnest(entities_to_add_in[1]),
        unnest(entities_to_add_in[2]),
        unnest(entities_to_add_in[3]),
        resource_id,
        resource_type,
        resource_provider,
        unnest(entities_to_add_in[4])
    ) on conflict do nothing;

    delete from provider.resource_acl_entry
    where
        (entity_type, project_id, group_id, username) in (
            select
                unnest(entities_to_remove_in[0]) as entity_type,
                unnest(entities_to_remove_in[1]) as project_id,
                unnest(entities_to_remove_in[2]) as group_id,
                unnest(entities_to_remove_in[4]) as username
        ) and
        resource_provider = resource_provider_in and
        resource_type = resource_type_in and
        resource_id = resource_id_in;

    return true;
end
$$;

create or replace function provider.approve_request(shared_secret_in text, public_key_in text, private_key_in text) returns provider.providers
	language plpgsql
as $$
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
$$;

-- Old grant service
create sequence if not exists "grant".application_id;

create sequence if not exists"grant".comment_id;

create sequence if not exists "grant".gift_id_sequence;

create table if not exists "grant".applications
(
	status text not null,
	resources_owned_by text not null,
	requested_by text not null,
	grant_recipient text not null,
	grant_recipient_type text not null,
	document text not null,
	created_at timestamp default now() not null,
	updated_at timestamp default now() not null,
	id bigint default nextval('"grant".application_id'::regclass) not null
		constraint applications_pkey
			primary key,
	status_changed_by text
);

create table if not exists "grant".requested_resources
(
	application_id bigint not null
		constraint requested_resources_application_id_fkey
			references "grant".applications,
	product_category text not null,
	product_provider text not null,
	credits_requested bigint,
	quota_requested_bytes bigint
);

create table if not exists "grant".comments
(
	application_id bigint not null
		constraint comments_application_id_fkey
			references "grant".applications,
	comment text not null,
	posted_by text not null,
	created_at timestamp default now() not null,
	id bigint default nextval('"grant".comment_id'::regclass) not null
		constraint comments_pkey
			primary key
);

create table if not exists "grant".allow_applications_from
(
	project_id text not null,
	type text not null,
	applicant_id text not null,
	constraint allow_applications_from_pkey
		primary key (project_id, type, applicant_id)
);

create table if not exists "grant".automatic_approval_users
(
	project_id text not null,
	type text not null,
	applicant_id text
);

create index if not exists automatic_approval_users_pid
	on "grant".automatic_approval_users (project_id);

create table if not exists "grant".automatic_approval_limits
(
	project_id text not null,
	product_category text not null,
	product_provider text not null,
	maximum_credits bigint,
	maximum_quota_bytes bigint
);

create index if not exists automatic_approval_limits_pid
	on "grant".automatic_approval_limits (project_id);

create table if not exists "grant".templates
(
	project_id text not null
		constraint templates_pkey
			primary key,
	personal_project text not null,
	existing_project text not null,
	new_project text not null
);

create table if not exists "grant".is_enabled
(
	project_id text not null
		constraint is_enabled_pkey
			primary key
);

create table if not exists "grant".gifts
(
	id bigint default nextval('"grant".gift_id_sequence'::regclass) not null
		constraint gifts_pkey
			primary key,
	resources_owned_by text not null,
	title text not null,
	description text not null
);

create table if not exists "grant".gifts_user_criteria
(
	gift_id bigint not null
		constraint gifts_user_criteria_gift_id_fkey
			references "grant".gifts,
	type text not null,
	applicant_id text not null,
	constraint gifts_user_criteria_pkey
		primary key (gift_id, type, applicant_id)
);

create table if not exists "grant".gift_resources
(
	gift_id bigint not null
		constraint gift_resources_gift_id_fkey
			references "grant".gifts,
	product_category text not null,
	product_provider text not null,
	credits bigint,
	quota bigint,
	constraint gift_resources_pkey
		primary key (gift_id, product_category, product_provider)
);

create table if not exists "grant".gifts_claimed
(
	gift_id bigint not null
		constraint gifts_claimed_gift_id_fkey
			references "grant".gifts,
	user_id text not null,
	constraint gifts_claimed_pkey
		primary key (gift_id, user_id)
);

create index if not exists gifts_claimed_user_id
	on "grant".gifts_claimed (user_id);

create table if not exists "grant".logos
(
	project_id text not null
		constraint logos_pkey
			primary key,
	data bytea
);

create table if not exists "grant".descriptions
(
	project_id text not null
		constraint descriptions_pkey
			primary key,
	description text
);

create table if not exists "grant".exclude_applications_from
(
	project_id text not null,
	email_suffix text not null,
	constraint exclude_applications_from_pkey
		primary key (project_id, email_suffix)
);

