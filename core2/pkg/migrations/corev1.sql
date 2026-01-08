create extension if not exists "uuid-ossp" schema public;

create extension if not exists "pgcrypto";

create extension if not exists ltree schema public;

create schema accounting;
create schema app_orchestrator;
create schema app_store;
create schema auth;
create schema avatar;
create schema file_orchestrator;
create schema "grant";
create schema mail;
create schema news;
create schema notification;
create schema password_reset;
create schema project;
create schema provider;
create schema support;
create schema task;

create sequence password_reset.hibernate_sequence;

create sequence notification.hibernate_sequence;

create sequence news.id_sequence;

create sequence auth.hibernate_sequence;

create sequence project.hibernate_sequence;

create sequence "grant".application_id;

create sequence "grant".comment_id;

create sequence "grant".gift_id_sequence;

create sequence task.hibernate_sequence;

create sequence app_store.hibernate_sequence;

create sequence app_store.tags_id_seq
    as integer;

create sequence app_store.overview_order_sequence;

create sequence app_orchestrator.hibernate_sequence;

create type provider.acl_entity_type as enum ('project_group', 'user');

create type "grant".transfer_output as
(
    source_title      text,
    destination_title text,
    recipient_title   text,
    user_to_notify    text
);


create type accounting.product_type as enum ('COMPUTE', 'STORAGE', 'INGRESS', 'LICENSE', 'NETWORK_IP', 'SYNCHRONIZATION');

create type accounting.charge_type as enum ('ABSOLUTE', 'DIFFERENTIAL_QUOTA');

create type accounting.product_price_unit as enum ('PER_UNIT', 'CREDITS_PER_MINUTE', 'CREDITS_PER_HOUR', 'CREDITS_PER_DAY', 'UNITS_PER_MINUTE', 'UNITS_PER_HOUR', 'UNITS_PER_DAY');

create type accounting.allocation_requests_group as enum ('ALL', 'PROJECT', 'PERSONAL');

create type file_orchestrator.metadata_template_namespace_type as enum ('COLLABORATORS', 'PER_USER');

create type file_orchestrator.share_state as enum ('APPROVED', 'PENDING', 'REJECTED');

create table support.flyway_schema_history
(
    installed_rank integer                 not null
        constraint flyway_schema_history_pk
            primary key,
    version        varchar(50),
    description    varchar(200)            not null,
    type           varchar(20)             not null,
    script         varchar(1000)           not null,
    checksum       integer,
    installed_by   varchar(100)            not null,
    installed_on   timestamp default now() not null,
    execution_time integer                 not null,
    success        boolean                 not null
);

create index flyway_schema_history_s_idx
    on support.flyway_schema_history (success);

create table password_reset.flyway_schema_history
(
    installed_rank integer                 not null
        constraint flyway_schema_history_pk
            primary key,
    version        varchar(50),
    description    varchar(200)            not null,
    type           varchar(20)             not null,
    script         varchar(1000)           not null,
    checksum       integer,
    installed_by   varchar(100)            not null,
    installed_on   timestamp default now() not null,
    execution_time integer                 not null,
    success        boolean                 not null
);

create index flyway_schema_history_s_idx
    on password_reset.flyway_schema_history (success);

create table password_reset.password_reset_requests
(
    token      varchar(128) not null
        primary key,
    user_id    varchar(255) not null,
    expires_at timestamp    not null
);

create table notification.flyway_schema_history
(
    installed_rank integer                 not null
        constraint flyway_schema_history_pk
            primary key,
    version        varchar(50),
    description    varchar(200)            not null,
    type           varchar(20)             not null,
    script         varchar(1000)           not null,
    checksum       integer,
    installed_by   varchar(100)            not null,
    installed_on   timestamp default now() not null,
    execution_time integer                 not null,
    success        boolean                 not null
);

create index flyway_schema_history_s_idx
    on notification.flyway_schema_history (success);

create table notification.notifications
(
    id          bigint  not null
        primary key,
    created_at  timestamp,
    message     varchar(255),
    meta        jsonb,
    modified_at timestamp,
    owner       varchar(255),
    read        boolean not null,
    type        varchar(255)
);

create index notifications_type_idx
    on notification.notifications (type);

create index notifications_created_at_idx
    on notification.notifications (created_at);

create table notification.subscriptions
(
    id        bigint                  not null
        primary key,
    hostname  varchar(2048),
    port      integer                 not null,
    username  varchar(2048),
    last_ping timestamp default now() not null
);

create table notification.notification_settings
(
    username text  not null
        primary key,
    settings jsonb not null
);

create table news.flyway_schema_history
(
    installed_rank integer                 not null
        constraint flyway_schema_history_pk
            primary key,
    version        varchar(50),
    description    varchar(200)            not null,
    type           varchar(20)             not null,
    script         varchar(1000)           not null,
    checksum       integer,
    installed_by   varchar(100)            not null,
    installed_on   timestamp default now() not null,
    execution_time integer                 not null,
    success        boolean                 not null
);

create index flyway_schema_history_s_idx
    on news.flyway_schema_history (success);

create table news.news
(
    id        bigint       not null
        primary key,
    title     varchar(255) not null,
    subtitle  varchar(255) not null,
    body      text         not null,
    posted_by varchar(255) not null,
    show_from timestamp    not null,
    hide_from timestamp,
    hidden    boolean      not null,
    category  varchar(255) not null
);

create table avatar.flyway_schema_history
(
    installed_rank integer                 not null
        constraint flyway_schema_history_pk
            primary key,
    version        varchar(50),
    description    varchar(200)            not null,
    type           varchar(20)             not null,
    script         varchar(1000)           not null,
    checksum       integer,
    installed_by   varchar(100)            not null,
    installed_on   timestamp default now() not null,
    execution_time integer                 not null,
    success        boolean                 not null
);

create index flyway_schema_history_s_idx
    on avatar.flyway_schema_history (success);

create table avatar.avatars
(
    username          varchar(255)                                     not null
        primary key,
    clothes           varchar(255),
    clothes_graphic   varchar(255),
    color_fabric      varchar(255),
    eyebrows          varchar(255),
    eyes              varchar(255),
    facial_hair       varchar(255),
    facial_hair_color varchar(255),
    hair_color        varchar(255),
    mouth_types       varchar(255),
    skin_colors       varchar(255),
    top               varchar(255),
    top_accessory     varchar(255),
    hat_color         varchar(255) default 'BLUE01'::character varying not null
);

create table auth.flyway_schema_history
(
    installed_rank integer                 not null
        constraint flyway_schema_history_pk
            primary key,
    version        varchar(50),
    description    varchar(200)            not null,
    type           varchar(20)             not null,
    script         varchar(1000)           not null,
    checksum       integer,
    installed_by   varchar(100)            not null,
    installed_on   timestamp default now() not null,
    execution_time integer                 not null,
    success        boolean                 not null
);

create index flyway_schema_history_s_idx
    on auth.flyway_schema_history (success);

create table auth.ott_black_list
(
    claimed_by varchar(255),
    jti        varchar(255) not null
        primary key
);

create table auth.principals
(
    dtype                     varchar(31) not null,
    id                        text        not null
        primary key,
    created_at                timestamp     default now(),
    modified_at               timestamp     default now(),
    role                      varchar(255),
    first_names               varchar(255)  default NULL::character varying,
    last_name                 varchar(255)  default NULL::character varying,
    hashed_password           bytea,
    salt                      bytea,
    org_id                    varchar(255)  default NULL::character varying,
    email                     varchar(1024) default NULL::character varying,
    service_license_agreement integer       default 0,
    uid                       serial
);

create unique index principals_uid_idx
    on auth.principals (uid);

create table auth.refresh_tokens
(
    token                    varchar(255)                               not null
        primary key,
    associated_user_id       varchar(255)
        constraint fkpsuddehavklxsnx2i22ybk1y6
            references auth.principals,
    csrf                     varchar(256) default ''::character varying not null,
    public_session_reference varchar(256)
        unique,
    extended_by              varchar(255) default NULL::character varying,
    scopes                   jsonb        default '["all:write"]'::jsonb,
    expires_after            bigint       default 600000                not null,
    refresh_token_expiry     bigint,
    extended_by_chain        jsonb        default '[]'::jsonb,
    created_at               timestamp,
    ip                       varchar(255),
    user_agent               varchar(4096)
);

create table auth.two_factor_credentials
(
    id            bigint       not null
        primary key,
    enforced      boolean      not null,
    shared_secret varchar(255) not null,
    principal_id  varchar(255) not null
        constraint fk2icqj501wstw3udr36ewytrpq
            references auth.principals
);

create table auth.two_factor_challenges
(
    dtype          varchar(31)  not null,
    challenge_id   varchar(255) not null
        primary key,
    expires_at     timestamp    not null,
    credentials_id bigint       not null
        constraint fk49sh77iu9qiv6u8on743bqcpj
            references auth.two_factor_credentials,
    service        varchar(255)
);

create table auth.cursor_state
(
    id         varchar(255) not null
        primary key,
    expires_at timestamp,
    hostname   varchar(255),
    port       integer      not null
);

create table auth.login_attempts
(
    id         bigint    not null
        primary key,
    created_at timestamp not null,
    username   varchar(4096)
);

create index login_attempts_username_idx
    on auth.login_attempts (username);

create table auth.login_cooldown
(
    id                 bigint        not null
        primary key,
    allow_logins_after timestamp     not null,
    expires_at         timestamp     not null,
    severity           integer       not null,
    username           varchar(4096) not null
);

create index login_cooldown_username_idx
    on auth.login_cooldown (username);

create table auth.providers
(
    id            text                    not null
        primary key,
    pub_key       text                    not null,
    priv_key      text                    not null,
    refresh_token text                    not null,
    claim_token   text                    not null
        unique,
    did_claim     boolean   default false not null,
    created_at    timestamp default now() not null
);

create table auth.verification_email_log
(
    ip_address text                                   not null,
    created_at timestamp with time zone default now() not null
);

create index verification_email_log_ip_address_idx
    on auth.verification_email_log (ip_address);

create table auth.user_info_update_request
(
    uid                integer
        references auth.principals (uid),
    first_names        text,
    last_name          text,
    email              text,
    created_at         timestamp with time zone default now(),
    modified_at        timestamp with time zone default now(),
    verification_token text                                   not null
        unique,
    confirmed          boolean                  default false not null
);

create table auth.identity_providers
(
    id                     serial
        primary key,
    title                  text                  not null
        unique,
    configuration          jsonb                 not null,
    counts_as_multi_factor boolean default false not null
);

create table auth.registration
(
    session_id               text                                   not null
        primary key,
    first_names              text,
    last_name                text,
    email                    text,
    email_verified           boolean                                not null,
    organization             text,
    created_at               timestamp with time zone default now() not null,
    modified_at              timestamp with time zone default now() not null,
    email_verification_token text,
    idp_identity             text                                   not null,
    identity_provider        integer                                not null
        references auth.identity_providers,
    organization_full_name   text,
    department               text,
    research_field           text,
    position                 text
);

create table auth.idp_connections
(
    principal         integer
        references auth.principals (uid),
    idp               integer not null
        references auth.identity_providers,
    provider_identity text    not null,
    organization_id   text,
    primary key (provider_identity, idp)
);

create table auth.additional_user_info
(
    associated_user        integer
        references auth.principals (uid),
    organization_full_name text,
    department             text,
    research_field         text,
    position               text
);

create unique index additional_user_info_associated_user_idx
    on auth.additional_user_info (associated_user);

create table auth.idp_auth_responses
(
    associated_user integer not null
        references auth.principals (uid),
    idp             integer not null
        references auth.identity_providers,
    idp_identity    text    not null,
    first_names     text,
    last_name       text,
    organization_id text,
    email           text,
    created_at      timestamp with time zone default now()
);

create index idp_auth_responses_associated_user_idp_idp_identity_idx
    on auth.idp_auth_responses (associated_user, idp, idp_identity);

create table provider.providers
(
    unique_name   text    not null,
    domain        text    not null,
    https         boolean not null,
    port          integer not null,
    refresh_token text,
    public_key    text,
    resource      bigint  not null
        primary key
);

create unique index unique_name_idx
    on provider.providers (unique_name);

create table project.projects
(
    id                     varchar(255) not null
        primary key,
    created_at             timestamp,
    modified_at            timestamp,
    title                  varchar(4096),
    archived               boolean default false,
    parent                 text
        references project.projects,
    dmp                    text,
    subprojects_renameable boolean default false,
    can_consume_resources  boolean default true,
    pid                    serial,
    provider_project_for   text
        references provider.providers (unique_name),
    constraint ensure_root_title_unique
        exclude (title with =)
);

create unique index project_id_case
    on project.projects (upper(id::text));

create unique index project_path_unique
    on project.projects (parent, upper(title::text));

create unique index projects_pid_idx
    on project.projects (pid);

create table project.project_members
(
    created_at  timestamp,
    modified_at timestamp,
    role        varchar(255),
    username    varchar(255) not null
        references auth.principals,
    project_id  varchar(255) not null
        references project.projects
            on delete cascade,
    primary key (username, project_id)
);

create table project.groups
(
    title   varchar(255)                                                                                          not null,
    project varchar(255)                                                                                          not null
        references project.projects,
    id      varchar(255) default (uuid_in((md5(((random())::text || (clock_timestamp())::text)))::cstring))::text not null
        primary key,
    gid     serial,
    unique (id, project)
);

create unique index group_title_uniq
    on project.groups (lower(title::text), project);

create unique index groups_gid_idx
    on project.groups (gid);

create table project.group_members
(
    username varchar(2048) not null
        references auth.principals,
    group_id varchar(255)  not null
        constraint group_members_group_fkey
            references project.groups,
    primary key (group_id, username)
);

create table project.project_membership_verification
(
    project_id   text      not null
        references project.projects,
    verification timestamp not null,
    verified_by  text,
    primary key (project_id, verification)
);

create table project.cooldowns
(
    username  text      not null
        references auth.principals,
    project   text      not null
        references project.projects,
    timestamp timestamp not null,
    primary key (username, project, timestamp)
);

create table project.project_favorite
(
    project_id text not null
        references project.projects
            on delete cascade,
    username   text not null
        references auth.principals,
    primary key (project_id, username),
    constraint project_favorite_project_members_id_fkey
        foreign key (project_id, username) references project.project_members (project_id, username)
            on delete cascade
);

create table project.invites
(
    project_id text not null
        references project.projects
            on delete cascade,
    username   text not null
        references auth.principals,
    invited_by text not null
        references auth.principals,
    created_at timestamp default now(),
    primary key (project_id, username)
);

create table provider.connected_with
(
    username    text not null
        references auth.principals,
    provider_id bigint
        constraint connected_with_fkey
            references provider.providers,
    expires_at  timestamp with time zone
);

create unique index connected_with_username_provider_id_idx
    on provider.connected_with (username, provider_id);

create table provider.approval_request
(
    shared_secret text    not null
        primary key,
    requested_id  text    not null
        unique,
    domain        text    not null,
    https         boolean not null,
    port          integer,
    signed_by     text
        references auth.principals,
    created_at    timestamp with time zone default now()
);

create table "grant".applications
(
    overall_state text                                                          not null,
    requested_by  text                                                          not null
        references auth.principals,
    created_at    timestamp default now()                                       not null,
    updated_at    timestamp default now()                                       not null,
    id            bigint    default nextval('"grant".application_id'::regclass) not null
        primary key,
    synchronized  boolean   default false
);

create table "grant".comments
(
    application_id bigint                                                    not null
        references "grant".applications,
    comment        text                                                      not null,
    posted_by      text                                                      not null
        references auth.principals,
    created_at     timestamp default now()                                   not null,
    id             bigint    default nextval('"grant".comment_id'::regclass) not null
        primary key
);

create table "grant".allow_applications_from
(
    project_id   text not null
        references project.projects,
    type         text not null,
    applicant_id text
);

create unique index allow_applications_from_uniq
    on "grant".allow_applications_from (project_id, type, COALESCE(applicant_id, ''::text));

create table "grant".templates
(
    project_id       text not null
        primary key
        references project.projects,
    personal_project text not null,
    existing_project text not null,
    new_project      text not null
);

create table "grant".is_enabled
(
    project_id text not null
        primary key
        references project.projects
);

create table "grant".gifts
(
    id                 bigint  default nextval('"grant".gift_id_sequence'::regclass) not null
        primary key,
    resources_owned_by text                                                          not null
        references project.projects,
    title              text                                                          not null,
    description        text                                                          not null,
    renewal_policy     integer default 0
);

create table "grant".gifts_user_criteria
(
    gift_id      bigint not null
        references "grant".gifts,
    type         text   not null,
    applicant_id text
);

create unique index gifts_user_criteria_uniq
    on "grant".gifts_user_criteria (gift_id, type, COALESCE(applicant_id, ''::text));

create table "grant".gifts_claimed
(
    gift_id    bigint not null
        references "grant".gifts,
    user_id    text   not null
        references auth.principals,
    claimed_at timestamp with time zone default now(),
    primary key (gift_id, user_id)
);

create index gifts_claimed_user_id
    on "grant".gifts_claimed (user_id);

create table "grant".logos
(
    project_id text not null
        primary key
        references project.projects,
    data       bytea
);

create table "grant".descriptions
(
    project_id  text not null
        primary key
        references project.projects,
    description text
);

create table "grant".exclude_applications_from
(
    project_id   text not null
        references project.projects,
    email_suffix text not null,
    primary key (project_id, email_suffix)
);

create table accounting.wallet_owner
(
    id         bigserial
        primary key,
    username   text
        references auth.principals,
    project_id text
        references project.projects,
    constraint check_only_one_owner
        check (((username IS NOT NULL) AND (project_id IS NULL)) OR ((username IS NULL) AND (project_id IS NOT NULL)))
);

create unique index wallet_owner_uniq
    on accounting.wallet_owner (COALESCE(username, ''::text), COALESCE(project_id, ''::text));

create table "grant".grant_giver_approvals
(
    application_id bigint not null
        references "grant".applications,
    project_id     text   not null,
    project_title  text,
    state          text,
    updated_by     text,
    last_update    timestamp,
    primary key (application_id, project_id)
);

create table "grant".revisions
(
    application_id   bigint                                 not null
        references "grant".applications,
    created_at       timestamp,
    updated_by       text                                   not null,
    revision_number  integer                  default 0     not null,
    revision_comment text,
    grant_start      timestamp with time zone default now() not null,
    grant_end        timestamp with time zone default now() not null,
    primary key (application_id, revision_number)
);

create table "grant".forms
(
    application_id    bigint not null
        references "grant".applications,
    revision_number   integer,
    parent_project_id text,
    recipient         text,
    recipient_type    text,
    form              text,
    reference_ids     text[],
    foreign key (application_id, revision_number) references "grant".revisions
);

create table project.invite_links
(
    token           uuid                      not null
        primary key,
    project_id      text                      not null
        references project.projects,
    expires         timestamp with time zone  not null,
    role_assignment text default 'USER'::text not null
);

create table project.invite_link_group_assignments
(
    link_token uuid         not null
        references project.invite_links
            on delete cascade,
    group_id   varchar(255) not null
        references project.groups,
    primary key (link_token, group_id)
);

create table accounting.accounting_units
(
    id                       bigserial
        primary key,
    name                     text    not null,
    name_plural              text    not null,
    floating_point           boolean not null,
    display_frequency_suffix boolean not null,
    constraint accounting_units_name_name_plural_floating_point_display_fr_key
        unique (name, name_plural, floating_point, display_frequency_suffix)
);

create table accounting.product_categories
(
    provider                       text                                                                                     not null
        references provider.providers (unique_name),
    category                       text                                                                                     not null,
    id                             bigserial
        primary key,
    product_type                   accounting.product_type                                                                  not null,
    charge_type                    accounting.charge_type               default 'ABSOLUTE'::accounting.charge_type          not null,
    unit_of_price                  accounting.product_price_unit,
    allow_allocation_requests_from accounting.allocation_requests_group default 'ALL'::accounting.allocation_requests_group not null,
    accounting_unit                bigint                                                                                   not null
        references accounting.accounting_units,
    accounting_frequency           text,
    free_to_use                    boolean                              default false,
    allow_sub_allocations          boolean                              default true,
    constraint product_categories_uniq
        unique (provider, category)
);

create unique index product_categories_id
    on accounting.product_categories (upper(provider), upper(category));

create table accounting.products
(
    name                         text                     not null,
    price                        bigint                   not null,
    description                  text    default ''::text not null,
    priority                     integer default 0,
    cpu                          integer,
    gpu                          integer,
    memory_in_gigs               integer,
    license_tags                 jsonb,
    hidden_in_grant_applications boolean default false    not null,
    category                     bigint                   not null
        constraint products_new_category_fkey
            references accounting.product_categories,
    id                           bigserial
        primary key,
    version                      bigint  default 1        not null,
    cpu_model                    text,
    gpu_model                    text,
    memory_model                 text
);

create unique index products_id
    on accounting.products (name, category, version);

create table provider.resource
(
    type                  text                                   not null,
    provider              text
        references provider.providers (unique_name),
    created_at            timestamp with time zone default now() not null,
    created_by            text
        references auth.principals,
    project               text
        references project.projects,
    id                    bigserial
        primary key,
    product               bigint
        references accounting.products,
    provider_generated_id text,
    confirmed_by_provider boolean                  default false not null,
    public_read           boolean                  default false not null,
    constraint provider_generated_id_unique
        unique (provider, provider_generated_id)
);

alter table provider.providers
    add foreign key (resource) references provider.resource;

create index resource_type_idx
    on provider.resource (type);

create table provider.resource_acl_entry
(
    group_id    text
        references project.groups,
    username    text
        references auth.principals,
    permission  text not null,
    resource_id bigint
        references provider.resource
);

create index resource_acl_entry_resource_id_idx
    on provider.resource_acl_entry (resource_id);

create unique index acl_entry_unique
    on provider.resource_acl_entry (COALESCE(username, ''::text), COALESCE(group_id, ''::text), resource_id, permission);


create table "grant".requested_resources
(
    application_id        bigint            not null
        references "grant".applications,
    credits_requested     bigint,
    quota_requested_bytes bigint,
    product_category      bigint            not null
        constraint requested_resources_product_category2_fkey
            references accounting.product_categories,
    start_date            timestamp,
    end_date              timestamp,
    grant_giver           text
        references project.projects,
    revision_number       integer default 0 not null,
    foreign key (application_id, revision_number) references "grant".revisions
);

create table "grant".gift_resources
(
    gift_id          bigint not null
        references "grant".gifts,
    credits          bigint,
    quota            bigint,
    product_category bigint not null
        constraint gift_resources_product_category2_fkey
            references accounting.product_categories
);

create table provider.resource_update
(
    id         bigserial
        primary key,
    resource   bigint
        references provider.resource,
    created_at timestamp with time zone default now(),
    status     text,
    extra      jsonb                    default '{}'::jsonb not null
);

create index resource_update_resource_idx
    on provider.resource_update (resource);

create table accounting.wallets_v2
(
    id                         bigserial
        primary key,
    wallet_owner               bigint
        references accounting.wallet_owner,
    product_category           bigint
        references accounting.product_categories,
    local_usage                bigint                   default 0     not null,
    local_retired_usage        bigint                   default 0     not null,
    excess_usage               bigint                   default 0     not null,
    total_allocated            bigint                   default 0     not null,
    total_retired_allocated    bigint                   default 0     not null,
    was_locked                 boolean                  default false not null,
    last_significant_update_at timestamp with time zone default now() not null,
    low_balance_notified       boolean                  default false not null
);

create type provider.accessible_resource as
(
    resource          provider.resource,
    product_name      text,
    product_category  text,
    product_provider  text,
    my_permissions    text[],
    other_permissions provider.resource_acl_entry[],
    updates           provider.resource_update[]
);
create table accounting.allocation_groups
(
    id                 bigserial
        primary key,
    parent_wallet      bigint
        references accounting.wallets_v2,
    associated_wallet  bigint           not null
        references accounting.wallets_v2,
    tree_usage         bigint default 0 not null,
    retired_tree_usage bigint default 0 not null
);

create index associated_wallet_to_group
    on accounting.allocation_groups (associated_wallet);

create table accounting.wallet_allocations_v2
(
    id                          bigserial
        primary key,
    associated_allocation_group bigint
        references accounting.allocation_groups,
    granted_in                  bigint
        references "grant".applications,
    quota                       bigint                   not null,
    allocation_start_time       timestamp with time zone not null,
    allocation_end_time         timestamp with time zone not null,
    retired                     boolean default false    not null,
    retired_usage               bigint
);

create index associated_group_to_allocation
    on accounting.wallet_allocations_v2 (associated_allocation_group);

create table accounting.intermediate_usage
(
    id        bigserial
        primary key,
    wallet_id bigint not null,
    usage     bigint not null
);

create table accounting.scoped_usage
(
    key   text   not null
        primary key,
    usage bigint not null
);

create table accounting.wallet_samples_v2
(
    sampled_at                  timestamp with time zone default now() not null,
    wallet_id                   bigint                                 not null
        references accounting.wallets_v2,
    quota                       bigint                                 not null,
    local_usage                 bigint                                 not null,
    excess_usage                bigint                   default 0     not null,
    tree_usage                  bigint                                 not null,
    group_ids                   integer[]                              not null,
    tree_usage_by_group         bigint[]                               not null,
    quota_by_group              bigint[]                               not null,
    retired_usage               bigint                   default 0,
    retired_tree_usage          bigint                   default 0,
    total_allocated             bigint                   default 0,
    total_retired_allocation    bigint                   default 0,
    was_locked                  boolean                  default false,
    retried_tree_usage_by_group bigint[]                 default ARRAY []::bigint[]
);

create index wallet_samples_v2_sampled_at_idx
    on accounting.wallet_samples_v2 (sampled_at);

create index wallet_samples_v2_wallet_id_idx
    on accounting.wallet_samples_v2 (wallet_id);

create table provider.reverse_connections
(
    token       text not null
        primary key,
    provider_id text not null
        references provider.providers (unique_name),
    created_at  timestamp with time zone default now()
);

create table task.flyway_schema_history
(
    installed_rank integer                 not null
        constraint flyway_schema_history_pk
            primary key,
    version        varchar(50),
    description    varchar(200)            not null,
    type           varchar(20)             not null,
    script         varchar(1000)           not null,
    checksum       integer,
    installed_by   varchar(100)            not null,
    installed_on   timestamp default now() not null,
    execution_time integer                 not null,
    success        boolean                 not null
);

create index flyway_schema_history_s_idx
    on task.flyway_schema_history (success);

create table task.tasks
(
    job_id         varchar(255) not null
        primary key,
    complete       boolean      not null,
    created_at     timestamp,
    modified_at    timestamp,
    owner          varchar(255),
    processor      varchar(255),
    status_message varchar(65536),
    title          varchar(65536)
);

create table task.tasks_v2
(
    id                  bigserial
        primary key,
    created_at          timestamp with time zone default now()            not null,
    modified_at         timestamp with time zone default now()            not null,
    created_by          text                                              not null
        references auth.principals,
    owned_by            text                                              not null
        references provider.providers (unique_name),
    state               text                     default 'IN_QUEUE'::text not null,
    progress            text,
    can_pause           boolean                  default false            not null,
    can_cancel          boolean                  default false            not null,
    progress_percentage real                     default '-1'::integer    not null,
    icon                varchar(64),
    body                text,
    title               text
);

create table file_orchestrator.flyway_schema_history
(
    installed_rank integer                 not null
        constraint flyway_schema_history_pk
            primary key,
    version        varchar(50),
    description    varchar(200)            not null,
    type           varchar(20)             not null,
    script         varchar(1000)           not null,
    checksum       integer,
    installed_by   varchar(100)            not null,
    installed_on   timestamp default now() not null,
    execution_time integer                 not null,
    success        boolean                 not null
);

create index flyway_schema_history_s_idx
    on file_orchestrator.flyway_schema_history (success);

create table file_orchestrator.file_collections
(
    resource bigint        not null
        constraint collections_pkey
            primary key
        constraint collections_resource_fkey
            references provider.resource,
    title    varchar(4096) not null
        constraint collections_title_check
            check (length((title)::text) > 0)
);

create unique index collections_resource_idx
    on file_orchestrator.file_collections (resource);

create table file_orchestrator.metadata_template_namespaces
(
    resource       bigint                                             not null
        primary key
        references provider.resource,
    uname          text                                               not null,
    namespace_type file_orchestrator.metadata_template_namespace_type not null,
    deprecated     boolean default false                              not null,
    latest_version text
);

create unique index metadata_template_namespaces_uname_idx
    on file_orchestrator.metadata_template_namespaces (uname);

create table file_orchestrator.metadata_templates
(
    title            text                                   not null,
    namespace        bigint                                 not null
        references file_orchestrator.metadata_template_namespaces,
    uversion         text                                   not null,
    schema           jsonb                                  not null,
    inheritable      boolean                                not null,
    require_approval boolean                                not null,
    description      text                                   not null,
    change_log       text                                   not null,
    ui_schema        jsonb,
    deprecated       boolean                  default false not null,
    created_at       timestamp with time zone default now(),
    primary key (namespace, uversion)
);

create table file_orchestrator.metadata_documents
(
    path                 text                    not null,
    parent_path          text                    not null,
    is_deletion          boolean,
    document             jsonb,
    change_log           text                    not null,
    created_by           text                    not null,
    workspace            text                    not null,
    is_workspace_project boolean,
    latest               boolean,
    approval_type        text                    not null,
    approval_updated_by  text,
    created_at           timestamp default now() not null,
    template_version     text                    not null,
    id                   serial
        primary key,
    template_id          bigint
        references file_orchestrator.metadata_template_namespaces,
    foreign key (template_id, template_version) references file_orchestrator.metadata_templates
);

create index latest_update_idx
    on file_orchestrator.metadata_documents (path, latest, workspace, is_workspace_project);

create index browse_idx
    on file_orchestrator.metadata_documents (parent_path, workspace, is_workspace_project);

create index path_idx
    on file_orchestrator.metadata_documents (path, workspace, is_workspace_project);

create type file_orchestrator.metadata_namespace_with_latest_title as
(
    resource  bigint,
    ns_in     file_orchestrator.metadata_template_namespaces,
    latest_in file_orchestrator.metadata_templates
);


create table file_orchestrator.shares
(
    resource           bigint not null
        primary key
        references provider.resource,
    shared_with        text   not null
        references auth.principals,
    permissions        text[] not null,
    original_file_path text   not null,
    available_at       text,
    state              file_orchestrator.share_state default 'PENDING'::file_orchestrator.share_state
);

create unique index shares_original_file_path_shared_with_idx
    on file_orchestrator.shares (original_file_path, shared_with);

create table file_orchestrator.sync_devices
(
    resource  bigint      not null
        primary key
        references provider.resource,
    device_id varchar(64) not null
);

create table file_orchestrator.sync_folders
(
    resource          bigint                  not null
        primary key
        references provider.resource,
    collection        bigint                  not null
        references provider.resource,
    sub_path          text                    not null,
    status_permission text                    not null,
    remote_device_id  text,
    last_scan         timestamp default now() not null
);

create type file_orchestrator.sync_with_dependencies as
(
    resource bigint,
    folder   file_orchestrator.sync_folders
);

create table file_orchestrator.shares_links
(
    token       uuid                            not null
        primary key,
    file_path   text                            not null,
    shared_by   text                            not null
        references auth.principals,
    expires     timestamp with time zone        not null,
    permissions text[] default '{READ}'::text[] not null
);

create table app_store.flyway_schema_history
(
    installed_rank integer                 not null
        constraint flyway_schema_history_pk
            primary key,
    version        varchar(50),
    description    varchar(200)            not null,
    type           varchar(20)             not null,
    script         varchar(1000)           not null,
    checksum       integer,
    installed_by   varchar(100)            not null,
    installed_on   timestamp default now() not null,
    execution_time integer                 not null,
    success        boolean                 not null
);

create index flyway_schema_history_s_idx
    on app_store.flyway_schema_history (success);

create table app_store.permissions
(
    application_name varchar(255)                               not null,
    username         varchar(255) default ''::character varying not null,
    permission       varchar(255)                               not null,
    project          varchar(255) default ''::character varying not null,
    project_group    varchar(255) default ''::character varying not null,
    primary key (username, project, project_group, application_name)
);

create table app_store.favorited_by
(
    the_user         varchar(255) not null,
    application_name varchar(255) not null,
    primary key (the_user, application_name)
);

create table app_store.spotlights
(
    id          serial
        primary key,
    title       text                  not null,
    description text                  not null,
    active      boolean default false not null
);

create table app_store.curators
(
    id                    text                     not null
        primary key,
    can_manage_catalog    boolean default false    not null,
    managed_by_project_id text                     not null,
    mandated_prefix       text    default ''::text not null
);

create table app_store.tools
(
    name              varchar(255)              not null,
    version           varchar(255)              not null,
    created_at        timestamp,
    modified_at       timestamp,
    original_document varchar(65536),
    owner             varchar(255),
    tool              jsonb,
    curator           text default 'main'::text not null
        references app_store.curators,
    primary key (name, version)
);

create table app_store.categories
(
    id       integer default nextval('app_store.tags_id_seq'::regclass) not null
        constraint tags_pkey
            primary key,
    tag      text                                                       not null,
    priority integer default 10000,
    curator  text    default 'main'::text                               not null
        references app_store.curators,
    public   boolean default false                                      not null
);

alter sequence app_store.tags_id_seq owned by app_store.categories.id;

create unique index tag_unique
    on app_store.categories (lower(tag));

create table app_store.application_groups
(
    id              serial
        primary key,
    title           text                         not null
        unique,
    logo            bytea,
    description     text,
    default_name    text,
    logo_has_text   boolean default false,
    color_remapping jsonb,
    curator         text    default 'main'::text not null
        references app_store.curators
);

create table app_store.applications
(
    name              varchar(255)                 not null,
    version           varchar(255)                 not null,
    application       jsonb,
    created_at        timestamp,
    modified_at       timestamp,
    original_document varchar(65536),
    owner             varchar(255),
    tool_name         varchar(255),
    tool_version      varchar(255),
    authors           jsonb,
    title             varchar(256),
    description       text,
    website           varchar(1024),
    is_public         boolean default true,
    group_id          integer
        references app_store.application_groups,
    flavor_name       text,
    tags              jsonb,
    curator           text    default 'main'::text not null
        references app_store.curators,
    primary key (name, version),
    constraint fkd3d72f8m75fv0xlhwwi8nqyvv
        foreign key (tool_name, tool_version) references app_store.tools
);

create index application_file_extensions
    on app_store.applications using gin ((application -> 'fileExtensions'::text) jsonb_path_ops);

create table app_store.category_items
(
    group_id integer not null
        constraint group_tags_group_id_fkey
            references app_store.application_groups,
    tag_id   integer not null
        constraint group_tags_tag_id_fkey
            references app_store.categories,
    constraint group_tags_pkey
        primary key (group_id, tag_id)
);

create table app_store.top_picks
(
    application_name text,
    group_id         integer
        references app_store.application_groups,
    description      text    not null,
    priority         integer not null
        primary key
);

create table app_store.spotlight_items
(
    spotlight_id     integer not null
        references app_store.spotlights,
    application_name text,
    group_id         integer
        references app_store.application_groups,
    description      text    not null,
    priority         integer not null
);

create unique index spotlight_items_spotlight_id_priority_idx
    on app_store.spotlight_items (spotlight_id, priority);

create table app_store.carrousel_items
(
    title              text    not null,
    body               text    not null,
    image_credit       text    not null,
    linked_application text,
    linked_group       integer
        references app_store.application_groups,
    linked_web_page    text,
    image              bytea   not null,
    priority           integer not null
        primary key
);

create table app_store.workflows
(
    id               bigserial
        primary key,
    created_at       timestamp with time zone default now() not null,
    modified_at      timestamp with time zone default now() not null,
    created_by       text                                   not null
        references auth.principals,
    project_id       text
        references project.projects,
    application_name text                                   not null,
    language         text                                   not null,
    is_open          boolean                                not null,
    path             text                                   not null,
    init             text,
    job              text,
    inputs           jsonb,
    readme           text
);

create index workflows_application_name_coalesce_idx
    on app_store.workflows (application_name, COALESCE(project_id, created_by));

create unique index workflows_coalesce_path_idx
    on app_store.workflows (COALESCE(project_id, created_by), application_name, path);

create table app_store.workflow_permissions
(
    workflow_id bigint
        references app_store.workflows,
    group_id    text not null
        references project.groups,
    permission  text not null,
    unique (workflow_id, group_id)
);

create table app_orchestrator.flyway_schema_history
(
    installed_rank integer                 not null
        constraint flyway_schema_history_pk
            primary key,
    version        varchar(50),
    description    varchar(200)            not null,
    type           varchar(20)             not null,
    script         varchar(1000)           not null,
    checksum       integer,
    installed_by   varchar(100)            not null,
    installed_on   timestamp default now() not null,
    execution_time integer                 not null,
    success        boolean                 not null
);

create index flyway_schema_history_s_idx
    on app_orchestrator.flyway_schema_history (success);

create table app_orchestrator.missed_payments
(
    reservation_id text      not null
        primary key,
    amount         bigint    not null,
    created_at     timestamp not null,
    type           text      not null
);

create table app_orchestrator.jobs
(
    application_name       text                    not null,
    application_version    text                    not null,
    time_allocation_millis bigint,
    replicas               integer   default 1     not null,
    name                   text,
    output_folder          text,
    last_scan              timestamp default now(),
    current_state          text                    not null,
    last_update            timestamp default now() not null,
    started_at             timestamp,
    resource               bigint                  not null
        primary key
        references provider.resource,
    job_parameters         jsonb,
    opened_file            text,
    restart_on_exit        boolean   default false not null,
    allow_restart          boolean   default false not null,
    ssh_enabled            boolean   default false not null
);

create table app_orchestrator.job_input_parameters
(
    name   text   not null,
    value  jsonb  not null,
    job_id bigint not null
        references app_orchestrator.jobs
);

create table app_orchestrator.job_resources
(
    resource jsonb  not null,
    job_id   bigint not null
        references app_orchestrator.jobs
);

create table app_orchestrator.ingresses
(
    domain          text                                not null,
    current_state   text     default 'PREPARING'::text,
    resource        bigint                              not null
        constraint ingress_pkey
            primary key
        references provider.resource,
    status_bound_to bigint[] default ARRAY []::bigint[] not null
);

create table app_orchestrator.licenses
(
    current_state   text     default 'PREPARING'::text,
    resource        bigint                              not null
        constraint license_pkey
            primary key
        references provider.resource,
    status_bound_to bigint[] default ARRAY []::bigint[] not null
);

create table app_orchestrator.network_ips
(
    current_state   text     default 'PREPARING'::text,
    firewall        jsonb    default '{"openPorts": []}'::jsonb,
    ip_address      text,
    resource        bigint                              not null
        constraint network_ip_pkey
            primary key
        references provider.resource,
    status_bound_to bigint[] default ARRAY []::bigint[] not null
);

create table app_orchestrator.ssh_keys
(
    id          bigserial
        primary key,
    owner       text                                                             not null
        references auth.principals,
    created_at  timestamp with time zone default now(),
    title       text                                                             not null,
    key         text                                                             not null,
    fingerprint text                     default 'Fingerprint unavailable'::text not null
);

create index ssh_keys_owner
    on app_orchestrator.ssh_keys (owner);

create table app_orchestrator.machine_support_info
(
    product_name     text    not null,
    product_category text    not null,
    product_provider text    not null,
    backend_type     text    not null,
    vnc              boolean not null,
    logs             boolean not null,
    terminal         boolean not null,
    time_extension   boolean not null,
    web              boolean not null,
    peers            boolean not null,
    suspend          boolean not null,
    primary key (product_name, product_category, product_provider)
);

-- SNIP
create function project.check_group_acl(uname text, is_admin boolean, group_filter text) returns boolean
    language plpgsql
as
$$
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
-- SNIP

-- SNIP
create function project.is_favorite(uname text, project text) returns boolean
    language plpgsql
as
$$
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
-- SNIP

-- SNIP
create function project.group_member_cleanup() returns trigger
    language plpgsql
as
$$
begin
    delete from project.group_members where username = old.username and group_id in
        (select id from project.groups where project = old.project_id);
    return null;
end;
$$;
-- SNIP

-- SNIP
create function project.is_admin(username_in text, project_id_in text) returns boolean
    language sql
as
$$
    select exists(
        select 1
        from project.project_members
        where
            username = username_in and
            (role = 'PI' or role = 'ADMIN') and
            project_id = project_id_in
    );
$$;
-- SNIP

-- SNIP
create function project.is_admin_of_parent(username_in text, project_id_in text) returns boolean
    language sql
as
$$
    select exists(
        select 1
        from
            project.projects child join
            project.projects parent on parent.id = child.parent join
            project.project_members members on parent.id = members.project_id
        where
            members.username = username_in and
            child.id = project_id_in and
            (role = 'PI' or role = 'ADMIN')
    );
$$;
-- SNIP

-- SNIP
create function project.view_ancestors(project_id_in text) returns text[]
    language plpgsql
as
$$
declare
    current_id text;
    result text[];
    r record;
begin
    current_id = project_id_in;
    while current_id is not null loop
        select id, title, parent
        into r
        from project.projects
        where id = current_id
        limit 1;

        if r.id is not null then
            result = result || array[r.id::text, r.title::text];
        end if;

        current_id = r.parent;
    end loop;
    return result;
end;
$$;
-- SNIP

-- SNIP
create function provider.generate_test_resources() returns void
    language plpgsql
as
$$
declare
    current_project text;
    current_group text;
    current_resource bigint;
    the_product bigint;
begin
    select id from accounting.products limit 1 into the_product;
    for current_project in (select id from project.projects tablesample system(40)) loop
        with random_users as (
            select username from project.project_members where project_id = current_project and random() <= 0.4
        )
        insert into provider.resource (type, provider, created_at, created_by, project, product)
        select 'test', null, now(), username, current_project, the_product
        from
            random_users,
            generate_series(0, 50) i;

        for current_resource in (select id from provider.resource where project = current_project) loop
            for current_group in (select id from project.groups where project = current_project and random() <= 0.4) loop
                insert into provider.resource_acl_entry (group_id, username, permission, resource_id)
                values (current_group, null, 'READ', current_resource);
            end loop;
        end loop;

    end loop;
end;
$$;
-- SNIP

-- SNIP
create function project.generate_test_project() returns text
    language plpgsql
as
$$
declare
    pid text;
    gid text;
begin
    select uuid_generate_v4() into pid;

    insert into project.projects(id, created_at, modified_at, title, parent, dmp) values(
        pid,
        now(),
        now(),
        'My Project: ' || random(),
        null,
        null
    );

    insert into auth.principals (dtype, id, created_at, modified_at, role, first_names, last_name, hashed_password, salt, org_id, email)
    select 'WAYF', 'pi' || pid, now(), now(), 'USER', 'U', 'U', null, null, 'sdu.dk', 'mail' || random() || '@mail.com';

    insert into auth.principals (dtype, id, created_at, modified_at, role, first_names, last_name, hashed_password, salt, org_id, email)
    select 'WAYF', 'user' || pid || i , now(), now(), 'USER', 'U', 'U', null, null, 'sdu.dk', 'mail' || random() || '@mail.com'
    from generate_series(0, 30) i;

    insert into project.project_members (created_at, modified_at, role, username, project_id) values
    (now(), now(), 'PI', 'pi' || pid, pid);

    insert into project.project_members (created_at, modified_at, role, username, project_id)
    select now(), now(), 'USER', 'user' || pid || i, pid
    from generate_series(0, 30) i;

    insert into project.groups (title, project)
    select 'Group' || i, pid
    from generate_series(0, 10) i;

    for gid in (select id from project.groups where project = pid) loop
        with random_users as (
            select username from project.project_members where project_id = pid and random() <= 0.3
        )
        insert into project.group_members (username, group_id)
        select username, gid from random_users;
    end loop;
    return pid;
end;
$$;
-- SNIP

-- SNIP
create function project.is_member(user_in text, project_in text) returns boolean
    language sql
as
$$
    select exists(
        select 1
        from project.projects p join project.project_members pm on p.id = pm.project_id
        where p.id = project_in and pm.username = user_in
    );
$$;
-- SNIP

-- SNIP
create function provider.update_acl(resource_id_in bigint, to_add_groups text[], to_add_users text[], to_add_permissions text[], to_remove_groups text[], to_remove_users text[]) returns void
    language plpgsql
as
$$
begin
    with removal_tuples as (
        select unnest(to_remove_groups) as group_id, unnest(to_remove_users) as username
    )
    delete from provider.resource_acl_entry e
    using removal_tuples t
    where
        (t.group_id is not null or t.username is not null) and -- sanity check
        (t.group_id is null or t.group_id = e.group_id) and
        (t.username is null or t.username = e.username) and
        e.resource_id = resource_id_in;

    insert into provider.resource_acl_entry
    (group_id, username, permission, resource_id)
    select unnest(to_add_groups), unnest(to_add_users), unnest(to_add_permissions), resource_id_in
    on conflict (coalesce(username, ''), coalesce(group_id, ''), resource_id, permission)
    do update set permission = excluded.permission;
end;
$$;
-- SNIP

-- SNIP
create function provider.default_delete(tbl regclass, resource_ids bigint[])
    returns TABLE(resource bigint)
    language plpgsql
as
$$
declare
    query text;
begin
    query = format('delete from %s where resource = some($1) returning resource', tbl);
    return query execute query using resource_ids;
end;
$$;
-- SNIP

-- SNIP
create function provider.jsonb_merge(currentdata jsonb, newdata jsonb) returns jsonb
    immutable
    language sql
as
$$
 select case jsonb_typeof(CurrentData)
   when 'object' then case jsonb_typeof(newData)
     when 'object' then (
       select    jsonb_object_agg(k, case
                   when e2.v is null then e1.v
                   when e1.v is null then e2.v
                   when e1.v = e2.v then e1.v
                   else provider.jsonb_merge(e1.v, e2.v)
                 end)
       from      jsonb_each(CurrentData) e1(k, v)
       full join jsonb_each(newData) e2(k, v) using (k)
     )
     else newData
   end
   when 'array' then CurrentData || newData
   else newData
 end
$$;
-- SNIP

-- SNIP
create function provider.resource_to_json(r provider.accessible_resource, additional jsonb) returns jsonb
    language sql
as
$$
    select provider.jsonb_merge(
        jsonb_build_object(
            'id', (r.resource).id::text,
            'createdAt', (floor(extract(epoch from (r.resource).created_at) * 1000)),
            'owner', jsonb_build_object(
                'createdBy', (r.resource).created_by,
                'project', (r.resource).project
            ),
            'status', jsonb_build_object(),
            'permissions', jsonb_build_object(
                'myself', r.my_permissions,
                'others', (
                    with transformed as (
                        select p.group_id, p.username, p.resource_id, array_agg(p.permission) as permissions
                        from unnest(r.other_permissions) p
                        group by p.group_id, p.username, p.resource_id
                    )
                    select jsonb_agg(
                        jsonb_build_object(
                            'entity', jsonb_build_object(
                                'type', case
                                    when p.group_id is null then 'user'
                                    else 'project_group'
                                end,
                                'group', p.group_id,
                                'username', p.username,
                                'projectId', (r.resource).project
                            ),
                            'permissions', p.permissions
                        )
                    )
                    from transformed p
                )
            ),
            'updates', (
                select coalesce(jsonb_agg(
                    jsonb_build_object(
                        'timestamp', (floor(extract(epoch from u.created_at) * 1000)),
                        'status', u.status
                    ) || u.extra
                ), '[]'::jsonb)
                from unnest(r.updates) u
            ),
            'specification', (
                jsonb_build_object('product', jsonb_build_object(
                    'id', coalesce(r.product_name, ''),
                    'category', coalesce(r.product_category, ''),
                    'provider', coalesce(r.product_provider, 'ucloud_core')
                ))
            ),
            'providerGeneratedId', (r.resource).provider_generated_id
        ),
        additional
    );
$$;
-- SNIP

-- SNIP
create function provider.accessible_resources(username_in text, resource_type_in text, required_permissions_one_of text[], resource_id bigint DEFAULT NULL::bigint, project_filter text DEFAULT ''::text, include_others boolean DEFAULT false, include_updates boolean DEFAULT false, include_unconfirmed boolean DEFAULT false) returns SETOF provider.accessible_resource
    stable
    rows 100
    language plpgsql
as
$fun$
declare
    query text;
begin
    query = $$
        select distinct
        r,
        the_product.name,
        p_cat.category,
        p_cat.provider,
        array_agg(
            distinct
            case
                when pm.role = 'PI' then 'ADMIN'
                when pm.role = 'ADMIN' then 'ADMIN'
                when r.created_by = $1 and r.project is null then 'ADMIN'
                when $1 = '#P_' || r.provider then 'PROVIDER'
                else acl.permission
            end
        ) as permissions,
    $$;

    if include_others then
        query = query || 'array_remove(array_agg(distinct other_acl), null),';
    else
        query = query || 'array[]::provider.resource_acl_entry[],';
    end if;

    if include_updates then
        -- TODO Fetching all updates might be a _really_ bad idea
        query = query || 'array_remove(array_agg(distinct u), null) as updates';
    else
        query = query || 'array[]::provider.resource_update[]';
    end if;

    query = query || $$
        from
            provider.resource r join
            accounting.products the_product on r.product = the_product.id join
            accounting.product_categories p_cat on the_product.category = p_cat.id left join
            provider.resource_acl_entry acl on r.id = acl.resource_id left join
            project.projects p on r.project = p.id left join
            project.project_members pm on p.id = pm.project_id and pm.username = $1 left join
            project.groups g on pm.project_id = g.project and acl.group_id = g.id left join
            project.group_members gm on g.id = gm.group_id and gm.username = $1
    $$;

    if include_others then
        query = query || ' left join provider.resource_acl_entry other_acl on r.id = other_acl.resource_id';
    end if;

    if include_updates then
        query = query || ' left join provider.resource_update u on r.id = u.resource';
    end if;

    query = query || $$
        where
            (confirmed_by_provider = true or $6) and
            ($5 = '' or $5 is not distinct from r.project) and
            ($4::bigint is null or r.id = $4) and
            r.type = $2  and
            (
                ($1 = '#P_' || r.provider) or
                (r.created_by = $1 and r.project is null) or
                (acl.username = $1) or
                (pm.role = 'PI' or pm.role = 'ADMIN') or
                (gm.username is not null)
           )
    $$;

    if include_others then
        query = query || ' and other_acl.username is distinct from $1 ';
    end if;

    query = query || $$
        group by r.*, the_product.name, p_cat.category, p_cat.provider
    $$;

    query = query || $$
        having
            $3 || array['ADMIN'] && array_agg(
                case
                    when pm.role = 'PI' then 'ADMIN'
                    when pm.role = 'ADMIN' then 'ADMIN'
                    when r.created_by = $1 and r.project is null then 'ADMIN'
                    when $1 = '#P_' || r.provider then 'PROVIDER'
                    else acl.permission
                end
            );
    $$;

    return query execute query using username_in, resource_type_in, required_permissions_one_of, resource_id,
        project_filter, include_unconfirmed;
end;
$fun$;
-- SNIP

-- SNIP
create function provider.default_browse(resource_type text, tbl regclass, sort_column text, to_json regproc, user_in text, project_in text DEFAULT ''::text, include_others boolean DEFAULT false, include_updates boolean DEFAULT false, include_unconfirmed boolean DEFAULT false) returns refcursor
    language plpgsql
as
$fun$
declare
    query text;
    c refcursor := 'c';
begin
    query = format('select provider.resource_to_json(r, %s(spec)) ', to_json);
    query = query || $$from provider.accessible_resources($1, $2, array['READ'], null, $3, $4, $5, $6) r join $$;
    query = query || format($$%s spec on (r.resource).id = spec.resource $$, tbl);
    query = query || format('order by spec.%I', sort_column);
    open c for execute query using user_in, resource_type, project_in, include_others, include_updates,
        include_unconfirmed;
    return c;
end;
$fun$;
-- SNIP

-- SNIP
create function provider.default_retrieve(resource_type text, tbl regclass, to_json regproc, user_in text, resource_id bigint, include_others boolean DEFAULT false, include_updates boolean DEFAULT false, include_unconfirmed boolean DEFAULT false) returns SETOF jsonb
    language plpgsql
as
$fun$
declare
    query text;
begin
    query = format('select provider.resource_to_json(r, %s(spec)) ', to_json);
    query = query || $$from provider.accessible_resources($1, $2, array['PROVIDER', 'READ'], $3, '', $4, $5, $6) r join $$;
    query = query || format($$%s spec on (r.resource).id = spec.resource $$, tbl);
    return query execute query using user_in, resource_type, resource_id, include_others, include_updates,
        include_unconfirmed;
end;
$fun$;
-- SNIP

-- SNIP
create function provider.default_bulk_retrieve(resource_type text, tbl regclass, to_json regproc, user_in text, resource_ids bigint[], permissions_one_of text[] DEFAULT ARRAY['READ'::text], include_others boolean DEFAULT false, include_updates boolean DEFAULT false, include_unconfirmed boolean DEFAULT false) returns SETOF jsonb
    language plpgsql
as
$fun$
declare
    query text;
begin
    query = format('select provider.resource_to_json(r, %s(spec)) ', to_json);
    query = query || $$from provider.accessible_resources($1, $2, $6, null, '', $4, $5, $7) r join $$;
    query = query || format($$%s spec on (r.resource).id = spec.resource $$, tbl);
    query = query || 'where spec.resource = some($3) ';
    return query execute query using user_in, resource_type, resource_ids, include_others,
        include_updates, permissions_one_of, include_unconfirmed;
end;
$fun$;
-- SNIP

-- SNIP
create function provider.provider_to_json(provider_in provider.providers) returns jsonb
    language sql
as
$$
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
-- SNIP

-- SNIP
create function provider.approve_request(shared_secret_in text, public_key_in text, private_key_in text, predefined_resource_id bigint) returns provider.providers
    language plpgsql
as
$$
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
           uuid_generate_v4()::text,
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

    select uuid_generate_v4() into generated_refresh_token;

    insert into provider.providers
        (unique_name, domain, https, port, refresh_token, public_key, resource)
    values (
        request.requested_id,
        request.domain,
        request.https,
        request.port,
        generated_refresh_token,
        public_key_in,
        resource_id
    ) returning * into result;

    insert into auth.providers(id, pub_key, priv_key, refresh_token, claim_token, did_claim)
    values (
        request.requested_id,
        public_key_in,
        private_key_in,
        generated_refresh_token,
        uuid_generate_v4()::text,
        true
    );

    return result;
end;
$$;
-- SNIP

-- SNIP
create function accounting.require_product_description() returns trigger
    language plpgsql
as
$$
begin
    if (new.description = '' or new.description is null) then
        raise exception 'description cannot be empty or null';
    end if;
    return null;
end;
$$;
-- SNIP

-- SNIP
create function accounting.require_immutable_product_category() returns trigger
    language plpgsql
as
$$
begin
    if old.charge_type != new.charge_type or old.product_type != new.product_type or old.unit_of_price != new.unit_of_price then
        raise exception 'Cannot change the definition of a category after its initial creation';
    end if;
    return null;
end;
$$;
-- SNIP

-- SNIP
create function accounting.require_fixed_price_per_unit_for_unit_per_x() returns trigger
    language plpgsql
as
$$
declare
    current_unit_of_price accounting.product_price_unit;
begin
    select pc.unit_of_price into current_unit_of_price
    from
        accounting.products p join
        accounting.product_categories pc on pc.id = p.category
    where
        p.id = new.id;

    if (current_unit_of_price = 'PER_UNIT' and
        new.price != 1) then
        raise exception 'Price per unit for PER_UNIT products can only be 1';
    end if;
    return null;
end;
$$;
-- SNIP

-- SNIP
create function accounting.wallet_owner_to_json(owner_in accounting.wallet_owner) returns jsonb
    language sql
as
$$
    select jsonb_build_object(
        'type', case
            when owner_in.username is not null then 'user'
            else 'project'
        end,
        'username', owner_in.username,
        'projectId', owner_in.project_id
    );
$$;
-- SNIP

-- SNIP
create function accounting.product_category_to_json(category_in accounting.product_categories) returns jsonb
    language sql
as
$$
    select jsonb_build_object(
        'name', category_in.category,
        'provider', category_in.provider
    );
$$;
-- SNIP

-- SNIP
create function provider.timestamp_to_unix(ts timestamp with time zone) returns double precision
    immutable
    language sql
as
$$
    select (floor(extract(epoch from ts) * 1000));
$$;
-- SNIP

-- SNIP
create function provider.first_agg(anyelement, anyelement) returns anyelement
    immutable
    strict
    parallel safe
    language sql
as
$$
        select $1;
$$;
-- SNIP

-- SNIP
create function provider.last_agg(anyelement, anyelement) returns anyelement
    immutable
    strict
    parallel safe
    language sql
as
$$
        select $2;
$$;
-- SNIP

-- SNIP
create function project.find_by_path(path_in text) returns project.projects[]
    language plpgsql
as
$$
declare
    i int;
    parent_needed text := null;
    current_project text;
    component text;
    components text[];
    result project.projects[];
begin
    components := regexp_split_to_array(path_in, '/');
    for i in array_lower(components, 1)..array_upper(components, 1) loop
        if i > 0 then
            parent_needed := result[i - 1].id;
        end if;

        component := components[i];

        select p into current_project
        from project.projects p
        where
            upper(title) = upper(component) and
            parent is not distinct from parent_needed;

        if current_project is null then
            return null;
        end if;

        result[i] := current_project;
    end loop;
    return result;
end;
$$;
-- SNIP

-- SNIP
create function "grant".resource_request_to_json(request_in "grant".requested_resources, product_category_in accounting.product_categories) returns jsonb
    language sql
as
$$
    select jsonb_build_object(
        'productCategory', product_category_in.category,
        'productProvider', product_category_in.provider,
        'balanceRequested', request_in.credits_requested
    );
$$;
-- SNIP

-- SNIP
create function "grant".can_submit_application(username_in text, source text, grant_recipient text, grant_recipient_type text) returns boolean
    language sql
as
$$
    with
        non_excluded_user as (
            select
                requesting_user.id, requesting_user.email, requesting_user.org_id
            from
                auth.principals requesting_user left join
                "grant".exclude_applications_from exclude_entry on
                    requesting_user.email like '%@' || exclude_entry.email_suffix and
                    exclude_entry.project_id = source
            where
                requesting_user.id = username_in
            group by
                requesting_user.id, requesting_user.email, requesting_user.org_id
            having
                count(email_suffix) = 0
        ),
        allowed_user as (
            select user_info.id
            from
                non_excluded_user user_info join
                "grant".allow_applications_from allow_entry on
                    allow_entry.project_id = source and
                    (
                        (
                            allow_entry.type = 'anyone'
                        ) or

                        (
                            allow_entry.type = 'wayf' and
                            allow_entry.applicant_id = user_info.org_id
                        ) or

                        (
                            allow_entry.type = 'email' and
                            user_info.email like '%@' || allow_entry.applicant_id
                        )
                    )
        ),

        existing_project_is_parent as (
            select existing_project.id
            from
                project.projects source_project join
                project.projects existing_project on
                    source_project.id = source and
                    source_project.id = existing_project.parent and
                    grant_recipient_type = 'existing_project' and
                    existing_project.id = grant_recipient join
                project.project_members pm on
                    pm.username = username_in and
                    pm.project_id = existing_project.id and
                    (
                        pm.role = 'ADMIN' or
                        pm.role = 'PI'
                    )
        )
    select coalesce(bool_or(allowed), false)
    from (
        select true allowed
        from
            allowed_user join
            "grant".is_enabled on
                is_enabled.project_id = source
        where allowed_user.id is not null

        union

        select true allowed
        from existing_project_is_parent
    ) t
$$;
-- SNIP

-- SNIP
create function "grant".application_status_trigger() returns trigger
    language plpgsql
as
$$
begin
    --
    if (
        (new.overall_state = old.overall_state) and
        (new.created_at = old.created_at) and
        (new.requested_by = old.requested_by)
        ) then
            return null;
    end if;
    if old.overall_state = 'APPROVED' or old.overall_state = 'CLOSED' then
        raise exception 'Cannot update a closed application';
    end if;
    return null;
end;
$$;
-- SNIP

-- SNIP
create function "grant".comment_to_json(comment_in "grant".comments) returns jsonb
    immutable
    language sql
as
$$
    select case when comment_in is null then null else jsonb_build_object(
        'id', comment_in.id,
        'username', comment_in.posted_by,
        'createdAt', (floor(extract(epoch from comment_in.created_at) * 1000)),
        'comment', comment_in.comment
    ) end
$$;
-- SNIP

-- SNIP
create function "grant".upload_request_settings(actor_in text, project_in text, new_exclude_list_in text[], new_include_list_type_in text[], new_include_list_entity_in text[], auto_approve_from_type_in text[], auto_approve_from_entity_in text[], auto_approve_resource_cat_name_in text[], auto_approve_resource_provider_name_in text[], auto_approve_credits_max_in bigint[], auto_approve_quota_max_in bigint[]) returns void
    language plpgsql
as
$$
declare
    can_update boolean := false;
begin
    if project_in is null then
        raise exception 'Missing project';
    end if;

    select count(*) > 0 into can_update
    from
        project.project_members pm join
        "grant".is_enabled enabled on pm.project_id = enabled.project_id
    where
        pm.username = actor_in and
        (pm.role = 'ADMIN' or pm.role = 'PI') and
        pm.project_id = project_in;

    if not can_update then
        raise exception 'Unable to update this project. Check if you are allowed to perform this operation.';
    end if;

    delete from "grant".exclude_applications_from
    where project_id = project_in;

    insert into "grant".exclude_applications_from (project_id, email_suffix)
    select project_in, unnest(new_exclude_list_in);

    delete from "grant".allow_applications_from
    where project_id = project_in;

    insert into "grant".allow_applications_from (project_id, type, applicant_id)
    select project_in, unnest(new_include_list_type_in), unnest(new_include_list_entity_in);

    delete from "grant".automatic_approval_users
    where project_id = project_in;

    insert into "grant".automatic_approval_users (project_id, type, applicant_id)
    select project_in, unnest(auto_approve_from_type_in), unnest(auto_approve_from_entity_in);

    delete from "grant".automatic_approval_limits
    where project_id = project_in;

    insert into "grant".automatic_approval_limits (project_id, maximum_credits, maximum_quota_bytes, product_category)
    with entries as (
        select
            unnest(auto_approve_resource_cat_name_in) category,
            unnest(auto_approve_resource_provider_name_in) provider,
            unnest(auto_approve_credits_max_in) credits,
            unnest(auto_approve_quota_max_in) quota
    )
    select project_in, credits, quota, pc.id
    from entries e join accounting.product_categories pc on e.category = pc.category and e.provider = pc.provider;
end;
$$;
-- SNIP

-- SNIP
create function "grant".delete_gift(actor_in text, gift_id_in bigint) returns void
    language plpgsql
as
$$
declare
    can_delete_gift boolean := false;
begin
    select count(*) > 0 into can_delete_gift
    from
        project.project_members pm join
        "grant".gifts gift on
            gift.id = gift_id_in and
            pm.project_id = gift.resources_owned_by and
            pm.username = actor_in and
            (pm.role = 'PI' or pm.role = 'ADMIN');

    if not can_delete_gift then
        raise exception 'Unable to delete gift. Are you an admin?';
    end if;

    delete from "grant".gifts_claimed where gift_id = gift_id_in;
    delete from "grant".gift_resources where gift_id = gift_id_in;
    delete from "grant".gifts_user_criteria where gift_id = gift_id_in;
    delete from "grant".gifts where id = gift_id_in;
end;
$$;
-- SNIP

-- SNIP
create function "grant".transfer_application(actor_in text, application_id_in bigint, target_project_in text) returns void
    language plpgsql
as
$$
declare
    affected_application record;
    update_count int;
begin
    select
        resources_owned_by, grant_recipient, grant_recipient_type, requested_by into affected_application
    from
        "grant".applications app join
        project.project_members pm on
            app.resources_owned_by = pm.project_id and
            pm.username = actor_in and
            (pm.role = 'PI' or pm.role = 'ADMIN')
    where
        id = application_id_in;

    update "grant".applications
    set resources_owned_by = target_project_in
    where
        "grant".can_submit_application(affected_application.requested_by, target_project_in,
            affected_application.grant_recipient, affected_application.grant_recipient_type)
    returning 1 into update_count;

    if update_count is null then
        raise exception 'Unable to transfer application (Not found or permission denied)';
    end if;

    if target_project_in = affected_application.resources_owned_by then
        raise exception 'Unable to transfer application to itself';
    end if;

    delete from "grant".requested_resources res
    using
        "grant".applications app join
        accounting.wallet_owner source_owner on
            app.id = res.application_id and
            source_owner.project_id = app.resources_owned_by join
        accounting.wallets source_wallet on
            res.product_category = source_wallet.category  and
            source_owner.id = source_wallet.owned_by join

        accounting.wallet_owner target_owner on
            target_owner.project_id = target_project_in left join
        accounting.wallets target_wallet on
            source_wallet.category = target_wallet.category and
            target_owner.project_id = target_wallet.owned_by
    where
        res.application_id = application_id_in and
        target_wallet.id is null;
end;
$$;
-- SNIP

-- SNIP
create function accounting.product_to_json(product_in accounting.products, category_in accounting.product_categories, balance bigint) returns jsonb
    language plpgsql
as
$$
declare
    builder jsonb;
begin
    builder := (
        select jsonb_build_object(
                       'category', accounting.product_category_to_json(category_in),
                       'pricePerUnit', product_in.price_per_unit,
                       'name', product_in.name,
                       'description', product_in.description,
                       'priority', product_in.priority,
                       'version', product_in.version,
                       'freeToUse', product_in.free_to_use,
                       'productType', category_in.product_type,
                       'unitOfPrice', category_in.unit_of_price,
                       'chargeType', category_in.charge_type,
                       'balance', balance,
                       'hiddenInGrantApplications', product_in.hidden_in_grant_applications,
                       'allowAllocationRequestsFrom', category_in.allow_allocation_requests_from
                   )
    );
    if category_in.product_type = 'STORAGE' then
        builder := builder || jsonb_build_object('type', 'storage');
    end if;
    if category_in.product_type = 'COMPUTE' then
        builder := builder || jsonb_build_object(
                'type', 'compute',
                'cpu', product_in.cpu,
                'gpu', product_in.gpu,
                'memoryInGigs', product_in.memory_in_gigs,
                'cpuModel', product_in.cpu_model,
                'memoryModel', product_in.memory_model,
                'gpuModel', product_in.gpu_model
            );
    end if;
    if category_in.product_type = 'INGRESS' then
        builder := builder || jsonb_build_object('type', 'ingress');
    end if;
    if category_in.product_type = 'LICENSE' then
        builder := builder || jsonb_build_object(
                'type', 'license',
                'tags', product_in.license_tags
            );
    end if;
    if category_in.product_type = 'NETWORK_IP' then
        builder := builder || jsonb_build_object('type', 'network_ip');
    end if;
    if category_in.product_type = 'SYNCHRONIZATION' then
        builder := builder || jsonb_build_object('type', 'synchronization');
    end if;

    return builder;
end
$$;
-- SNIP

-- SNIP
create function project.group_to_json(group_in project.groups, members_in project.group_members[]) returns jsonb
    language sql
as
$$
    select jsonb_build_object(
        'id', group_in.id,
        'createdAt', 0, -- TODO This is currently not known
        'specification', jsonb_build_object(
            'project', group_in.project,
            'title', group_in.title
        ),
        'status', jsonb_build_object(
            'members', array_remove((select array_agg(t.username) from unnest(members_in) as t), null)
        )
    );
$$;
-- SNIP

-- SNIP
create function project.project_to_json(project_in project.projects, groups_in project.groups[], group_members_in project.group_members[], members_in project.project_members[], is_favorite_in boolean, my_role_in text) returns jsonb
    language sql
as
$$
    select jsonb_build_object(
        'id', project_in.id,
        'createdAt', provider.timestamp_to_unix(project_in.created_at),
        'modifiedAt', provider.timestamp_to_unix(project_in.modified_at),
        'specification', jsonb_build_object(
            'parent', project_in.parent,
            'title', project_in.title,
            'canConsumeResources', project_in.can_consume_resources
        ),
        'status', jsonb_build_object(
            'myRole', my_role_in,
            'archived', project_in.archived,
            'isFavorite', is_favorite_in,
            'members', (
                select array_agg(jsonb_build_object('username', t.username, 'role', t.role))
                from unnest(members_in) as t
            ),
            'settings', jsonb_build_object(
                'subprojects', jsonb_build_object(
                    'allowRenaming', project_in.subprojects_renameable
                )
            ),
            'groups', (
                with
                    groups as (select g from unnest(groups_in) g),
                    group_members as (select gm from unnest(group_members_in) gm),
                    jsonified as (
                        select project.group_to_json(g, array_agg(distinct gm)) js
                        from
                            groups g left join
                            group_members gm on (g.g).id = (gm.gm).group_id
                        where
                            g.g is not null
                        group by
                            g
                    )
                select coalesce(array_remove(array_agg(js), null), array[]::jsonb[])
                from jsonified
            ),
            'personalProviderProjectFor', project_in.provider_project_for
        )
    );
$$;
-- SNIP

-- SNIP
create function "grant".resource_allocation_to_json(request_in "grant".requested_resources, product_category_in accounting.product_categories) returns jsonb
    language sql
as
$$
    with title as (
        select id, title
        from project.projects p
        where request_in.grant_giver = p.id
    )
select jsonb_build_object(
               'category', product_category_in.category,
               'provider', product_category_in.provider,
               'grantGiver', request_in.grant_giver,
               'balanceRequested', request_in.credits_requested,
               'period', jsonb_build_object(
                       'start', provider.timestamp_to_unix(request_in.start_date),
                       'end', provider.timestamp_to_unix(request_in.end_date)
                         ),
               'grantGiverTitle', title.title
       )
    from title;
$$;
-- SNIP

-- SNIP
create function "grant".grant_giver_approval_to_json(grant_giver_approvals_in "grant".grant_giver_approvals) returns jsonb
    language sql
as
$$
    select jsonb_build_object(
        'projectId', grant_giver_approvals_in.project_id,
        'projectTitle', grant_giver_approvals_in.project_title,
        'state', grant_giver_approvals_in.state
    );
$$;
-- SNIP

-- SNIP
create function "grant".form_to_json(form_in "grant".forms) returns jsonb
    language plpgsql
as
$$
    begin
        return jsonb_build_object(
            'type', 'plain_text',
            'text', form_in.form
        );
    end
$$;
-- SNIP

-- SNIP
create function "grant".revision_to_json(resources_in jsonb[], form_in "grant".forms, revision_in "grant".revisions) returns jsonb
    language plpgsql
as
$$
declare
    builder jsonb;
    document jsonb;
begin

    if form_in.recipient_type = 'personal' then
        document := jsonb_build_object(
                'recipient', jsonb_build_object(
                        'type', 'personalWorkspace',
                        'username', form_in.recipient
                             ),
                'allocationRequests', resources_in,
                'form', "grant".form_to_json(form_in),
                'referenceIds', form_in.reference_ids,
                'revisionComment', revision_in.revision_comment,
                'parentProjectId', form_in.parent_project_id,
                'allocationPeriod', jsonb_build_object(
                    'start', (floor(extract(epoch from revision_in.grant_start) * 1000)),
                    'end', (floor(extract(epoch from revision_in.grant_end) * 1000))
                )
        );
    elseif form_in.recipient_type = 'existing_project' then
        document := jsonb_build_object(
                'recipient', jsonb_build_object(
                        'type', 'existingProject',
                        'id', form_in.recipient
                             ),
                'allocationRequests', resources_in,
                'form', "grant".form_to_json(form_in),
                'referenceIds', form_in.reference_ids,
                'revisionComment', revision_in.revision_comment,
                'parentProjectId', form_in.parent_project_id,
                'allocationPeriod', jsonb_build_object(
                    'start', (floor(extract(epoch from revision_in.grant_start) * 1000)),
                    'end', (floor(extract(epoch from revision_in.grant_end) * 1000))
                )
        );
    elseif form_in.recipient_type = 'new_project' then
        document := jsonb_build_object(
                'recipient', jsonb_build_object(
                        'type', 'newProject',
                        'title', form_in.recipient
                             ),
                'allocationRequests', resources_in,
                'form', "grant".form_to_json(form_in),
                'referenceIds', form_in.reference_ids,
                'revisionComment', revision_in.revision_comment,
                'parentProjectId', form_in.parent_project_id,
                'allocationPeriod', jsonb_build_object(
                    'start', (floor(extract(epoch from revision_in.grant_start) * 1000)),
                    'end', (floor(extract(epoch from revision_in.grant_end) * 1000))
                )
        );
    end if;

    builder := jsonb_build_object(
            'createdAt', (floor(extract(epoch from revision_in.created_at) * 1000)),
            'updatedBy', revision_in.updated_by,
            'revisionNumber', revision_in.revision_number,
            'document', document
               );

    return builder;
end;
$$;
-- SNIP

-- SNIP
create function "grant".application_to_json(app_id_in bigint) returns jsonb
    language sql
as
$$
with max_revision_number as (
    select max(r.revision_number) newest
    from "grant".revisions r
    where r.application_id = app_id_in
    group by application_id
),
     max_revision as (
         select *
         from
             max_revision_number n join
             "grant".revisions r on
                 r.application_id = app_id_in and
                 r.revision_number = n.newest
     ),
     current_revision as (
         select
             "grant".revision_to_json(
                     array_remove(array_agg((
                         case
                             when not(rr is null) then "grant".resource_allocation_to_json(rr, pc)
                             else null
                             end
                         )), null),
                     f,
                     r
             )
                 as result
         from  "grant".applications a join
               max_revision mr on mr.application_id = a.id join
               "grant".forms f on f.application_id = a.id and
                                  f.revision_number = mr.newest join
               "grant".revisions r on r.revision_number = mr.newest and
                                      r.application_id = mr.application_id left join
               "grant".requested_resources rr on a.id = rr.application_id and
                                                 rr.revision_number = mr.newest left join
               accounting.product_categories pc on rr.product_category = pc.id
         where a.id = app_id_in
         group by f, r
     ),
     all_revisions as (
         select array_agg(revisions.results) as revs, app_id_in as appid from (
           select  "grant".revision_to_json(
                           array_remove(array_agg((
                               case
                                   when not (rr is null) then "grant".resource_allocation_to_json(rr, pc)
                                   else null
                                   end
                               )), null),
                           f,
                           r
                   ) as results
           from "grant".applications a join
                "grant".revisions r on
                    r.application_id = a.id join
                "grant".forms f on
                    f.revision_number = r.revision_number and
                    f.application_id = r.application_id left join
                "grant".requested_resources rr on
                    r.application_id = rr.application_id and
                    r.revision_number = rr.revision_number left join
                accounting.product_categories pc on
                    rr.product_category = pc.id
           where a.id = app_id_in
           group by r.*, f.revision_number, f.*
           order by f.revision_number
       ) as revisions
    )
select jsonb_build_object(
               'id', resolved_application.id,
               'createdAt', (floor(extract(epoch from resolved_application.created_at) * 1000)),
               'updatedAt', (floor(extract(epoch from latest_revision.created_at)  * 1000)),
               'currentRevision', (select result from current_revision limit 1),
               'createdBy', resolved_application.requested_by,
               'status', jsonb_build_object(
                       'overallState', resolved_application.overall_state,
                       'stateBreakdown', array_remove(array_agg(distinct ("grant".grant_giver_approval_to_json(approval_status))), null),
                       'comments', array_remove(array_agg(distinct ("grant".comment_to_json(posted_comment))), null),
                       'revisions', revision.revs,
                       'projectTitle', p.title,
                       'projectPI', coalesce(pm.username,resolved_application.requested_by)
                         )
       )
from
    all_revisions revision join
    "grant".applications resolved_application on revision.appid = resolved_application.id join
    max_revision latest_revision on
        resolved_application.id = latest_revision.application_id join
    "grant".forms latest_form on
        latest_form.revision_number = latest_revision.newest and
        latest_form.application_id = latest_revision.application_id join
    "grant".grant_giver_approvals approval_status on
        resolved_application.id = approval_status.application_id left join
    "grant".comments posted_comment on resolved_application.id = posted_comment.application_id left join
    project.projects p on p.id = latest_form.recipient left join
    project.project_members pm on p.id = pm.project_id and pm.role = 'PI'
group by
    resolved_application.id,
    resolved_application.*,
    latest_revision.created_at,
    latest_revision.*,
    latest_form.*,
    revision.revs,
    p.title,
    pm.username;
$$;
-- SNIP

-- SNIP
create function "grant".can_submit_application(username_in text, sources text[], grant_recipient text, grant_recipient_type text) returns boolean
    language sql
as
$$
    with
        non_excluded_user as (
            select
                requesting_user.id, requesting_user.email, requesting_user.org_id
            from
                auth.principals requesting_user left join
                "grant".exclude_applications_from exclude_entry on
                    requesting_user.email like '%@' || exclude_entry.email_suffix and
                    exclude_entry.project_id in (select unnest(sources))
            where
                requesting_user.id = username_in
            group by
                requesting_user.id, requesting_user.email, requesting_user.org_id
            having
                count(email_suffix) = 0
        ),
        allowed_user as (
            select user_info.id
            from
                non_excluded_user user_info join
                "grant".allow_applications_from allow_entry on
                    allow_entry.project_id in (select unnest(sources)) and
                    (
                        (
                            allow_entry.type = 'anyone'
                        ) or

                        (
                            allow_entry.type = 'wayf' and
                            allow_entry.applicant_id = user_info.org_id
                        ) or

                        (
                            allow_entry.type = 'email' and
                            user_info.email like '%@' || allow_entry.applicant_id
                        )
                    )
        ),

        existing_project_is_parent as (
            select existing_project.id
            from
                project.projects source_project join
                project.projects existing_project on
                    source_project.id in (select unnest(sources)) and
                    source_project.id = existing_project.parent and
                    grant_recipient_type = 'existing_project' and
                    existing_project.id = grant_recipient join
                project.project_members pm on
                    pm.username = username_in and
                    pm.project_id = existing_project.id and
                    (
                        pm.role = 'ADMIN' or
                        pm.role = 'PI'
                    )
        )
    select coalesce(bool_or(allowed), false)
    from (
        select true allowed
        from
            allowed_user join
            "grant".is_enabled on
                is_enabled.project_id in (select unnest(sources))
        where allowed_user.id is not null

        union

        select true allowed
        from existing_project_is_parent
    ) t
$$;
-- SNIP

-- SNIP
create function "grant".approve_application(application_id_in bigint, parent_project_id_in text) returns void
    language plpgsql
as
$$
declare
    created_project text;
begin
    -- NOTE(Dan): Start by finding all source allocations and target allocation information.
    -- NOTE(Dan): We currently pick source allocation using an "expire first" policy. This might need to change in the
    -- future.
    create temporary table approve_result on commit drop as
        with max_revisions as (
            select application_id, max(revision_number) as newest
            from "grant".revisions
            where application_id = application_id_in
            group by application_id
            order by application_id
        )
        select
            app.id application_id,
            app.requested_by,
            f.recipient,
            f.recipient_type,
            resource.grant_giver,
            resource.source_allocation,
            alloc.id allocation_id,
            resource.credits_requested,
            alloc.start_date,
            alloc.end_date
        from
            max_revisions
            join "grant".applications app on
                max_revisions.application_id = app.id
            join "grant".forms f on
                app.id = f.application_id and
                max_revisions.newest = f.revision_number
            join "grant".requested_resources resource on
                max_revisions.application_id = resource.application_id and
                max_revisions.newest = resource.revision_number
            join accounting.wallet_allocations alloc on
                resource.source_allocation = alloc.id
            join accounting.wallet_owner wo on
                resource.grant_giver = wo.project_id
        where
            app.overall_state = 'APPROVED' and
            app.id = application_id_in;


    -- NOTE(Dan): Create a project, if the grant_recipient_type = 'new_project'
    insert into project.projects (id, created_at, modified_at, title, archived, parent, dmp, subprojects_renameable)
    select uuid_generate_v4()::text, now(), now(), recipient, false, parent_project_id_in, null, false
    from approve_result
    where recipient_type = 'new_project'
    limit 1
    returning id into created_project;

    insert into project.project_members (created_at, modified_at, role, username, project_id)
    select now(), now(), 'PI', requested_by, created_project
    from approve_result
    where recipient_type = 'new_project'
    limit 1;

    create temporary table grant_created_projects(project_id text primary key) on commit drop;
    insert into grant_created_projects(project_id) select created_project where created_project is not null;

    -- NOTE(Dan): Run the normal deposit procedure
    perform accounting.deposit(array_agg(req))
    from (
        select (
            '_ucloud',
            case
                when result.recipient_type = 'new_project' then created_project
                when result.recipient_type = 'existing_project' then result.recipient
                else result.recipient
            end,
            case
                when result.recipient_type = 'new_project' then true
                when result.recipient_type = 'existing_project' then true
                else false
            end,
            allocation_id,
            result.credits_requested,
            result.start_date,
            result.end_date,
            'Grant application approved',
            concat('_ucloud', '-', uuid_generate_v4()),
            result.application_id
        )::accounting.deposit_request req
        from approve_result result
    ) t;
end;
$$;
-- SNIP

-- SNIP
create function "grant".transfer_application(actor_in text, application_id_in bigint, source_project_id_in text, target_project_in text, newest_revision_in integer, revision_comment_in text) returns void
    language plpgsql
as
$$
begin
    if target_project_in = source_project_id_in then
        raise exception 'Unable to transfer application to itself';
    end if;

    with resources_affected as (
        select
            app.id, rr.credits_requested, rr.product_category, rr.start_date, rr.end_date, f.recipient, f.recipient_type, target_project_in as parent_project_id, f.form, f.reference_id, requested_by
        from
            "grant".applications app join
            "grant".revisions rev on
                app.id = rev.application_id and
                rev.revision_number = newest_revision_in join
            "grant".requested_resources rr on
                rr.revision_number = rev.revision_number and
                rr.application_id = app.id and
                rr.grant_giver = source_project_id_in join
            "grant".forms f on
                app.id = f.application_id and
                rev.revision_number = f.revision_number join
            project.project_members pm on
                rr.grant_giver = pm.project_id and
                pm.username = actor_in and
                (pm.role = 'PI' or pm.role = 'ADMIN')
        where
            id = application_id_in
    ),
    update_revision as (
        insert into "grant".revisions (
            application_id, revision_number, created_at, updated_by, revision_comment
        )
        select
            id,
            (newest_revision_in+1),
            now(),
            actor_in,
            revision_comment_in
        from resources_affected
        limit(1)
    ),
    insert_resources as (
        insert into "grant".requested_resources (
            application_id,
            credits_requested,
            quota_requested_bytes,
            product_category,
            source_allocation,
            start_date,
            end_date,
            grant_giver,
            revision_number
        )
        select
            id,
            credits_requested,
            null,
            product_category,
            null,
            start_date,
            end_date,
            target_project_in,
            (newest_revision_in+1)
        from resources_affected
    ),
    update_last_revision_ressources_not_from_source as (
        insert into "grant".requested_resources (
            application_id,
            credits_requested,
            quota_requested_bytes,
            product_category,
            source_allocation,
            start_date,
            end_date,
            grant_giver,
            revision_number
        )
        select
            app.id,
            rr.credits_requested,
            rr.quota_requested_bytes,
            rr.product_category,
            rr.source_allocation,
            rr.start_date,
            rr.end_date,
            rr.grant_giver,
            (newest_revision_in +1)
        from
            "grant".applications app join
            "grant".requested_resources rr on
                rr.revision_number = newest_revision_in and
                rr.application_id = app.id and
                rr.grant_giver != source_project_id_in
        where
            id = application_id_in
    ),
    project_title as (
        select title
        from project.projects
        where id = target_project_in
        limit 1
    ),
    update_grant_givers as (
        update "grant".grant_giver_approvals
        set project_id = target_project_in, project_title = project_title.title
        from project_title
        where
            project_id = source_project_id_in and
            application_id = application_id_in
    )
    insert into "grant".forms (
        application_id,
        revision_number,
        parent_project_id,
        recipient,
        recipient_type,
        form,
        reference_id
    )
    select
        id,
        (newest_revision_in + 1),
        parent_project_id,
        recipient,
        recipient_type,
        form,
        reference_id
    from resources_affected
    limit 1;
end;
$$;
-- SNIP

-- SNIP
create function "grant".upload_request_settings(actor_in text, project_in text, new_exclude_list_in text[], new_include_list_type_in text[], new_include_list_entity_in text[], auto_approve_from_type_in text[], auto_approve_from_entity_in text[], auto_approve_resource_cat_name_in text[], auto_approve_resource_provider_name_in text[], auto_approve_credits_max_in bigint[], auto_approve_quota_max_in bigint[], auto_approve_grant_giver_in text[]) returns void
    language plpgsql
as
$$
declare
    can_update boolean := false;
begin
    if project_in is null then
        raise exception 'Missing project';
    end if;

    select count(*) > 0 into can_update
    from
        project.project_members pm join
        "grant".is_enabled enabled on pm.project_id = enabled.project_id
    where
        pm.username = actor_in and
        (pm.role = 'ADMIN' or pm.role = 'PI') and
        pm.project_id = project_in;

    if not can_update then
        raise exception 'Unable to update this project. Check if you are allowed to perform this operation.';
    end if;

    delete from "grant".exclude_applications_from
    where project_id = project_in;

    insert into "grant".exclude_applications_from (project_id, email_suffix)
    select project_in, unnest(new_exclude_list_in);

    delete from "grant".allow_applications_from
    where project_id = project_in;

    insert into "grant".allow_applications_from (project_id, type, applicant_id)
    select project_in, unnest(new_include_list_type_in), unnest(new_include_list_entity_in);

    delete from "grant".automatic_approval_users
    where project_id = project_in;

    insert into "grant".automatic_approval_users (project_id, type, applicant_id)
    select project_in, unnest(auto_approve_from_type_in), unnest(auto_approve_from_entity_in);

    delete from "grant".automatic_approval_limits
    where project_id = project_in;

    insert into "grant".automatic_approval_limits (project_id, maximum_credits, maximum_quota_bytes, product_category, grant_giver)
    with entries as (
        select
            unnest(auto_approve_resource_cat_name_in) category,
            unnest(auto_approve_resource_provider_name_in) provider,
            unnest(auto_approve_credits_max_in) credits,
            unnest(auto_approve_quota_max_in) quota,
            unnest(auto_approve_grant_giver_in) grant_giver
    )
    select project_in, credits, quota, pc.id, grant_giver
    from entries e join accounting.product_categories pc on e.category = pc.category and e.provider = pc.provider;
end;
$$;
-- SNIP

-- SNIP
create function "grant".upload_request_settings(actor_in text, project_in text, new_exclude_list_in text[], new_include_list_type_in text[], new_include_list_entity_in text[]) returns void
    language plpgsql
as
$$
declare
    can_update boolean := false;
begin
    if project_in is null then
        raise exception 'Missing project';
    end if;

    select count(*) > 0 into can_update
    from
        project.project_members pm join
        "grant".is_enabled enabled on pm.project_id = enabled.project_id
    where
        pm.username = actor_in and
        (pm.role = 'ADMIN' or pm.role = 'PI') and
        pm.project_id = project_in;

    if not can_update then
        raise exception 'Unable to update this project. Check if you are allowed to perform this operation.';
    end if;

    delete from "grant".exclude_applications_from
    where project_id = project_in;

    insert into "grant".exclude_applications_from (project_id, email_suffix)
    select project_in, unnest(new_exclude_list_in);

    delete from "grant".allow_applications_from
    where project_id = project_in;

    insert into "grant".allow_applications_from (project_id, type, applicant_id)
    select project_in, unnest(new_include_list_type_in), unnest(new_include_list_entity_in);
end;
$$;
-- SNIP

-- SNIP
create function accounting.product_category_to_json(pc accounting.product_categories, au accounting.accounting_units) returns jsonb
    language sql
as
$$
    select jsonb_build_object(
        'id', pc.id,
        'name', pc.category,
        'provider', pc.provider,
        'accountingFrequency', pc.accounting_frequency,
        'productType', pc.product_type,
        'accountingUnit', json_build_object(
            'name', au.name,
            'namePlural', au.name_plural,
            'floatingPoint', au.floating_point,
            'displayFrequencySuffix', au.display_frequency_suffix
        ),
        'freeToUse', pc.free_to_use,
        'allowSubAllocations', pc.allow_sub_allocations
    );
$$;
-- SNIP

-- SNIP
create function accounting.product_to_json(product_in accounting.products, category_in accounting.product_categories, unit_in accounting.accounting_units, balance bigint) returns jsonb
    language plpgsql
as
$$
declare
    builder jsonb;
begin
    builder := (
        select jsonb_build_object(
            'category', accounting.product_category_to_json(category_in, unit_in),
            'name', product_in.name,
            'description', product_in.description,
            'productType', category_in.product_type,
            'price', product_in.price,
            'balance', balance,
            'hiddenInGrantApplications', product_in.hidden_in_grant_applications
        )
    );
    if category_in.product_type = 'STORAGE' then
        builder := builder || jsonb_build_object('type', 'storage');
    end if;
    if category_in.product_type = 'COMPUTE' then
        builder := builder || jsonb_build_object(
            'type', 'compute',
            'cpu', product_in.cpu,
            'gpu', product_in.gpu,
            'memoryInGigs', product_in.memory_in_gigs,
            'cpuModel', product_in.cpu_model,
            'memoryModel', product_in.memory_model,
            'gpuModel', product_in.gpu_model
        );
    end if;
    if category_in.product_type = 'INGRESS' then
        builder := builder || jsonb_build_object('type', 'ingress');
    end if;
    if category_in.product_type = 'LICENSE' then
        builder := builder || jsonb_build_object(
            'type', 'license',
            'tags', product_in.license_tags
        );
    end if;
    if category_in.product_type = 'NETWORK_IP' then
        builder := builder || jsonb_build_object('type', 'network_ip');
    end if;
    if category_in.product_type = 'SYNCHRONIZATION' then
        builder := builder || jsonb_build_object('type', 'synchronization');
    end if;

    return builder;
end
$$;
-- SNIP

-- SNIP
create function "grant".create_gift(actor_in text, gift_resources_owned_by_in text, title_in text, description_in text, renewal integer, criteria_type_in text[], criteria_entity_in text[], resource_cat_name_in text[], resource_provider_name_in text[], resources_credits_in bigint[], resources_quota_in bigint[]) returns bigint
    language plpgsql
as
$$
declare
    can_create_gift boolean := false;
    created_gift_id bigint;
begin
    select count(*) > 0 into can_create_gift
    from
        project.project_members pm
    where
        pm.project_id = gift_resources_owned_by_in and
        pm.username = actor_in and
        (pm.role = 'ADMIN' or pm.role = 'PI');

    if not can_create_gift then
        raise exception 'Unable to create a gift in this project. Are you an admin?';
    end if;

    insert into "grant".gifts (resources_owned_by, title, description, renewal_policy)
    values (gift_resources_owned_by_in, title_in, description_in, renewal)
    returning id into created_gift_id;

    insert into "grant".gifts_user_criteria (gift_id, type, applicant_id)
    select created_gift_id, unnest(criteria_type_in), unnest(criteria_entity_in);

    insert into "grant".gift_resources (gift_id, credits, quota, product_category)
    with entries as (
        select unnest(resource_cat_name_in) category, unnest(resource_provider_name_in) provider,
               unnest(resources_quota_in) quota, unnest(resources_credits_in) credits
    )
    select created_gift_id, e.credits, e.quota, pc.id
    from
        entries e join
        accounting.product_categories pc on
            e.category = pc.category and
            e.provider = pc.provider;

    return created_gift_id;
end;
$$;
-- SNIP

-- SNIP
create function file_orchestrator.metadata_document_to_json(doc file_orchestrator.metadata_documents) returns jsonb
    language plpgsql
as
$$
DECLARE approval jsonb;
begin
    approval = jsonb_build_object(
        'type', doc.approval_type
    );

    if doc.approval_type = 'approved' then
        approval = jsonb_set(approval, array['approvedBy']::text[], to_jsonb(doc.approval_updated_by), true);
    elseif doc.approval_type = 'rejected' then
        approval = jsonb_set(approval, array['rejectedBy']::text[], to_jsonb(doc.approval_updated_by), true);
    end if;

    if doc.is_deletion then
        return jsonb_build_object(
            'type', 'deleted',
            'id', doc.id,
            'changeLog', doc.change_log,
            'createdAt', floor(extract(epoch from doc.created_at) * 1000),
            'createdBy', doc.created_by,
            'status', jsonb_build_object(
                'approval', approval
            )
        );
    else
        return jsonb_build_object(
            'type', 'metadata',
            'id', doc.id,
            'specification', jsonb_build_object(
                'templateId', doc.template_id,
                'document', doc.document,
                'version', doc.template_version,
                'changeLog', doc.change_log
            ),
            'createdAt', floor(extract(epoch from doc.created_at) * 1000),
            'status', jsonb_build_object(
                'approval', approval
            ),
            'createdBy', doc.created_by
        );
    end if;
end;
$$;
-- SNIP

-- SNIP
create function file_orchestrator.file_collection_to_json(collection_in file_orchestrator.file_collections) returns jsonb
    language sql
as
$$
    select jsonb_build_object(
        'specification', jsonb_build_object(
            'title', collection_in.title
        )
    );
$$;
-- SNIP

-- SNIP
create function file_orchestrator.metadata_template_to_json(ns_in file_orchestrator.metadata_template_namespaces, template_in file_orchestrator.metadata_templates) returns jsonb
    language sql
as
$$
    select jsonb_build_object(
        'namespaceId', template_in.namespace,
        'namespaceName', ns_in.uname,
        'title', template_in.title,
        'version', template_in.uversion,
        'schema', template_in.schema,
        'inheritable', template_in.inheritable,
        'requireApproval', template_in.require_approval,
        'description', template_in.description,
        'changeLog', template_in.change_log,
        'uiSchema', template_in.ui_schema,
        'namespaceType', ns_in.namespace_type,
        'createdAt', floor(extract(epoch from template_in.created_at) * 1000)
    );
$$;
-- SNIP

-- SNIP
create function file_orchestrator.metadata_template_namespace_to_json(r_in file_orchestrator.metadata_namespace_with_latest_title) returns jsonb
    language sql
as
$$
    select jsonb_build_object(
        'specification', jsonb_build_object(
            'name', (r_in.ns_in).uname,
            'namespaceType', (r_in.ns_in).namespace_type
        ),
        'status', jsonb_build_object(
            'latestTitle', (r_in.latest_in).title
        )
    );
$$;
-- SNIP

-- SNIP
create function file_orchestrator.array_drop_last(count integer, arr anyarray) returns anyarray
    immutable
    strict
    language sql
as
$$
    select array(
        select arr[i]
        from generate_series(
            array_lower(arr, 1),
            array_upper(arr, 1) - count
        ) as s(i)
        order by i
    )
$$;
-- SNIP

-- SNIP
create function file_orchestrator.parent_file(file_path text) returns text
    immutable
    strict
    language sql
as
$$
    select array_to_string(file_orchestrator.array_drop_last(1, regexp_split_to_array(file_path, '/')), '/');
$$;
-- SNIP

-- SNIP
create function file_orchestrator.share_to_json(share_in file_orchestrator.shares) returns jsonb
    language sql
as
$$
    select jsonb_build_object(
        'specification', jsonb_build_object(
            'sourceFilePath', share_in.original_file_path,
            'sharedWith', share_in.shared_with,
            'permissions', share_in.permissions
        ),
        'status', jsonb_build_object(
            'shareAvailableAt', share_in.available_at,
            'state', share_in.state
        )
    )
$$;
-- SNIP

-- SNIP
create function file_orchestrator.sync_device_to_json(device_in file_orchestrator.sync_devices) returns jsonb
    language sql
as
$$
select jsonb_build_object(
    'specification', jsonb_build_object(
        'deviceId', device_in.device_id
    )
);
$$;
-- SNIP

-- SNIP
create function file_orchestrator.sync_folder_to_json(sync_in file_orchestrator.sync_with_dependencies) returns jsonb
    language sql
as
$$
select jsonb_build_object(
    'specification', jsonb_build_object(
        'path', '/' || (sync_in.folder).collection || (sync_in.folder).sub_path
    ),
    'status', jsonb_build_object(
        'remoteDevice', (sync_in.folder).remote_device_id,
        'permission', (sync_in.folder).status_permission
    )
);
$$;
-- SNIP

-- SNIP
create function file_orchestrator.remove_sync_folders(ids bigint[]) returns void
    language sql
as
$$
    delete from file_orchestrator.sync_folders where resource in (select unnest(ids));
    delete from provider.resource_acl_entry where resource_id in (select unnest(ids));
    delete from provider.resource_update where resource in (select unnest(ids));
    delete from provider.resource where id in (select unnest(ids));
$$;
-- SNIP

-- SNIP
create function app_store.require_favorite_app_exists() returns trigger
    language plpgsql
as
$$
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
-- SNIP

-- SNIP
create function app_store.require_default_app_exists() returns trigger
    language plpgsql
as
$$
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
-- SNIP

-- SNIP
create function app_orchestrator.update_license_state() returns trigger
    language plpgsql
as
$$
begin
    update app_orchestrator.licenses
    set current_state   = coalesce(new.state, current_state),
        last_update     = now()
    where id = new.license_id
      and new.timestamp >= last_update;
    return null;
end;
$$;
-- SNIP

-- SNIP
create function app_orchestrator.update_network_ip_state() returns trigger
    language plpgsql
as
$$
begin
    update app_orchestrator.network_ips
    set current_state   = coalesce(new.state, current_state),
        status_bound_to =
            case new.change_binding
                when true then new.bound_to
                else status_bound_to
                end,
        ip_address      =
            case new.change_ip_address
                when true then new.new_ip_address
                else ip_address
                end,
        last_update     = now()
    where id = new.network_ip_id
      and new.timestamp >= last_update;
    return null;
end;
$$;
-- SNIP

-- SNIP
create function app_orchestrator.ingress_to_json(ingress_in app_orchestrator.ingresses) returns jsonb
    language sql
as
$$
    select jsonb_build_object(
        'specification', jsonb_build_object(
            'domain', ingress_in.domain
        ),
        'status', jsonb_build_object(
            'boundTo', ingress_in.status_bound_to,
            'state', ingress_in.current_state
        )
    );
$$;
-- SNIP

-- SNIP
create function app_store.tool_to_json(tool app_store.tools) returns jsonb
    language sql
as
$$
    select jsonb_build_object(
        'owner', tool.owner,
        'createdAt', floor(extract(epoch from tool.created_at) * 1000),
        'modifiedAt', floor(extract(epoch from tool.modified_at) * 1000),
        'description', tool.tool || jsonb_build_object(
            'info', jsonb_build_object(
                'name', tool.name,
                'version', tool.version
            )
        )
    );
$$;
-- SNIP

-- SNIP
create function app_store.application_to_json(app app_store.applications, tool app_store.tools DEFAULT NULL::app_store.tools) returns jsonb
    language sql
as
$$
    select jsonb_build_object(
        'metadata', jsonb_build_object(
            'name', app.name,
            'version', app.version,
            'authors', app.authors,
            'title', app.title,
            'description', app.description,
            'website', app.website,
            'public', app.is_public
        ),
        'invocation', app.application || jsonb_build_object(
            'tool', jsonb_build_object(
                'name', app.tool_name,
                'version', app.version,
                'tool', app_store.tool_to_json(tool)
            )
        )
    )
$$;
-- SNIP



create type app_orchestrator.job_with_dependencies as
(
    resource    bigint,
    job         app_orchestrator.jobs,
    resources   app_orchestrator.job_resources[],
    parameters  app_orchestrator.job_input_parameters[],
    application app_store.applications,
    tool        app_store.tools
);


-- SNIP
create function app_orchestrator.job_to_json(job_with_deps app_orchestrator.job_with_dependencies) returns jsonb
    language sql
as
$$
    select jsonb_build_object(
        'specification', jsonb_build_object(
            'application', jsonb_build_object(
                'name', (job_with_deps.job).application_name,
                'version', (job_with_deps.job).application_version
            ),
            'name', (job_with_deps.job).name,
            'replicas', (job_with_deps.job).replicas,
            'timeAllocation', case
                when ((job_with_deps.job).time_allocation_millis) is null then null
                else jsonb_build_object(
                    'hours', ((job_with_deps.job).time_allocation_millis) / (1000 * 60 * 60),
                    'minutes', (((job_with_deps.job).time_allocation_millis) % (1000 * 60 * 60)) / (1000 * 60),
                    'seconds', ((((job_with_deps.job).time_allocation_millis) % (1000 * 60 * 60)) % (1000 * 60) / 1000)
                )
            end,
            'parameters', (
                select coalesce(jsonb_object_agg(p.name, p.value), '{}'::jsonb)
                from unnest(job_with_deps.parameters) p
            ),
            'resources', (
                select coalesce(jsonb_agg(r.resource), '[]'::jsonb)
                from unnest(job_with_deps.resources) r
            ),
            'openedFile', (job_with_deps.job).opened_file,
            'restartOnExit', (job_with_deps.job).restart_on_exit,
            'sshEnabled', (job_with_deps.job).ssh_enabled
        ),
        'output', jsonb_build_object(
            'outputFolder', (job_with_deps.job).output_folder
        ),
        'status', jsonb_build_object(
            'state', (job_with_deps.job).current_state,
            'startedAt', floor(extract(epoch from (job_with_deps.job).started_at) * 1000),
            'expiresAt', floor(extract(epoch from (job_with_deps.job).started_at) * 1000) +
                (job_with_deps.job).time_allocation_millis,
            'resolvedApplication', app_store.application_to_json(job_with_deps.application, job_with_deps.tool),
            'jobParametersJson', (job_with_deps.job).job_parameters,
            'allowRestart', (job_with_deps.job).allow_restart
        )
    )
$$;
-- SNIP

-- SNIP
create function app_orchestrator.license_to_json(license_in app_orchestrator.licenses) returns jsonb
    language sql
as
$$
    select jsonb_build_object(
        'status', jsonb_build_object(
            'boundTo', license_in.status_bound_to,
            'state', license_in.current_state
        )
    );
$$;
-- SNIP

-- SNIP
create function app_orchestrator.network_ip_to_json(network_in app_orchestrator.network_ips) returns jsonb
    language sql
as
$$
    select jsonb_build_object(
        'specification', jsonb_build_object('firewall', network_in.firewall),
        'status', jsonb_build_object(
            'boundTo', network_in.status_bound_to,
            'state', network_in.current_state,
            'ipAddress', network_in.ip_address
        )
    );
$$;
-- SNIP

-- SNIP
create function provider.notify_resource_updated() returns trigger
    language plpgsql
as
$$
begin
    perform pg_notify('resource_eviction', new.type || '-' || coalesce(p.pid, 0) || '-' || u.uid)
    from
        auth.principals u
        left join project.projects p on new.project = p.id
    where
        u.id = new.created_by;
    return new;
end;
$$;
-- SNIP

-- SNIP
create function provider.notify_resource_deleted() returns trigger
    language plpgsql
as
$$
begin
    perform pg_notify('resource_eviction', old.type || '-' || coalesce(p.pid, 0) || '-' || u.uid)
    from
        auth.principals u
        left join project.projects p on new.project = p.id
    where
        u.id = old.created_by;
    return old;
end;
$$;
-- SNIP

-- SNIP
create function provider.notify_resource_acl_updated() returns trigger
    language plpgsql
as
$$
begin
    perform pg_notify('resource_eviction', r.type || '-' || coalesce(p.pid, 0) || '-' || u.uid)
    from
        provider.resource r
        join auth.principals u on u.id = r.created_by
        left join project.projects p on r.project = p.id
    where
        r.id = new.resource_id;
    return new;
end;
$$;
-- SNIP

-- SNIP
create function provider.notify_resource_acl_deleted() returns trigger
    language plpgsql
as
$$
begin
    perform pg_notify('resource_eviction', r.type || '-' || coalesce(p.pid, 0) || '-' || u.uid)
    from
        provider.resource r
        join auth.principals u on u.id = r.created_by
        left join project.projects p on r.project = p.id
    where
        r.id = old.resource_id;
    return old;
end;
$$;
-- SNIP

-- SNIP
create function provider.notify_resource_update_updated() returns trigger
    language plpgsql
as
$$
begin
    perform pg_notify('resource_eviction', r.type || '-' || coalesce(p.pid, 0) || '-' || u.uid)
    from
        provider.resource r
        join auth.principals u on u.id = r.created_by
        left join project.projects p on r.project = p.id
    where
        r.id = new.resource;
    return new;
end;
$$;
-- SNIP

-- SNIP
create function provider.notify_resource_update_deleted() returns trigger
    language plpgsql
as
$$
begin
    perform pg_notify('resource_eviction', r.type || '-' || coalesce(p.pid, 0) || '-' || u.uid)
    from
        provider.resource r
        join auth.principals u on u.id = r.created_by
        left join project.projects p on r.project = p.id
    where
        r.id = old.resource;
    return old;
end;
$$;
-- SNIP

-- SNIP
create function app_orchestrator.jobs_resource_updated() returns trigger
    language plpgsql
as
$$
                        begin
                            perform pg_notify('resource_eviction', r.type || '-' || coalesce(p.pid, 0) || '-' || u.uid)
                            from
                                provider.resource r
                                join auth.principals u on u.id = r.created_by
                                left join project.projects p on r.project = p.id
                            where
                                r.id = new.resource;
                            return new;
                        end;
                        $$;
-- SNIP

-- SNIP
create function app_orchestrator.jobs_resource_deleted() returns trigger
    language plpgsql
as
$$
                        begin
                            perform pg_notify('resource_eviction', r.type || '-' || coalesce(p.pid, 0) || '-' || u.uid)
                            from
                                provider.resource r
                                join auth.principals u on u.id = r.created_by
                                left join project.projects p on r.project = p.id
                            where
                                r.id = old.resource;
                            return old;
                        end;
                        $$;
-- SNIP

-- SNIP
create function app_orchestrator.job_resources_resource_updated() returns trigger
    language plpgsql
as
$$
                        begin
                            perform pg_notify('resource_eviction', r.type || '-' || coalesce(p.pid, 0) || '-' || u.uid)
                            from
                                provider.resource r
                                join auth.principals u on u.id = r.created_by
                                left join project.projects p on r.project = p.id
                            where
                                r.id = new.job_id;
                            return new;
                        end;
                        $$;
-- SNIP

-- SNIP
create function app_orchestrator.job_resources_resource_deleted() returns trigger
    language plpgsql
as
$$
                        begin
                            perform pg_notify('resource_eviction', r.type || '-' || coalesce(p.pid, 0) || '-' || u.uid)
                            from
                                provider.resource r
                                join auth.principals u on u.id = r.created_by
                                left join project.projects p on r.project = p.id
                            where
                                r.id = old.job_id;
                            return old;
                        end;
                        $$;
-- SNIP

-- SNIP
create function app_orchestrator.job_input_parameters_resource_updated() returns trigger
    language plpgsql
as
$$
                        begin
                            perform pg_notify('resource_eviction', r.type || '-' || coalesce(p.pid, 0) || '-' || u.uid)
                            from
                                provider.resource r
                                join auth.principals u on u.id = r.created_by
                                left join project.projects p on r.project = p.id
                            where
                                r.id = new.job_id;
                            return new;
                        end;
                        $$;
-- SNIP

-- SNIP
create function app_orchestrator.job_input_parameters_resource_deleted() returns trigger
    language plpgsql
as
$$
                        begin
                            perform pg_notify('resource_eviction', r.type || '-' || coalesce(p.pid, 0) || '-' || u.uid)
                            from
                                provider.resource r
                                join auth.principals u on u.id = r.created_by
                                left join project.projects p on r.project = p.id
                            where
                                r.id = old.job_id;
                            return old;
                        end;
                        $$;
-- SNIP

create aggregate provider.first(anyelement) (
    sfunc = provider.first_agg,
    stype = anyelement
    );

create aggregate provider.last(anyelement) (
    sfunc = provider.last_agg,
    stype = anyelement
    );

create table mail.email_settings
(
    username text  not null
        primary key,
    settings jsonb not null
);

create table mail.mail_counting
(
    mail_count   bigint                not null,
    username     text                  not null
        primary key,
    period_start timestamp             not null,
    alerted_for  boolean default false not null
);

-- DATA INIT

insert into auth.principals
    (dtype, id, created_at, modified_at, role, first_names, last_name, hashed_password,
     salt, org_id, email)
values
    ('SERVICE', '_ucloud', now(), now(), 'SERVICE', null, null, null, null, null, null)
on conflict do nothing;

insert into auth.principals
    (dtype, id, created_at, modified_at, role, first_names, last_name, hashed_password, salt, org_id, email)
values
    ('PASSWORD', 'ghost', now(), now(), 'USER', 'Invalid', 'Invalid', E'\\xDEADBEEF', E'\\xDEADBEEF', null, 'ghost@escience.sdu.dk');


delete from app_store.tools where name = 'unknown' and version = 'unknown';
delete from app_store.applications where name = 'unknown' and version = 'unknown';

INSERT INTO app_store.curators (id, can_manage_catalog, managed_by_project_id, mandated_prefix) VALUES ('main', true, '', '');

-- SNIP
insert into app_store.tools
    (name, version, created_at, modified_at, original_document, owner, tool)
values
    (
        'unknown', 'unknown',
        now(), now(),
        '{}',
        'ghost',
        $$
        {
          "info": {
            "name": "unknown",
            "version": "unknown"
          },
          "image": "alpine:3",
          "title": "Unknown Application",
          "authors": [
            "UCloud"
          ],
          "backend": "DOCKER",
          "license": "",
          "container": "alpine:3",
          "description": "This job was started outside of UCloud or with an application which no longer exists.",
          "requiredModules": [],
          "supportedProviders": null,
          "defaultNumberOfNodes": 1,
          "defaultTimeAllocation": {
            "hours": 1,
            "minutes": 0,
            "seconds": 0
          }
        }
        $$
    );
-- SNIP

-- SNIP
insert into app_store.applications
    (name, version, created_at, modified_at, original_document, owner, tool_name, tool_version, authors, tags, title, description, website, application)
values
    (
        'unknown', 'unknown',
        now(), now(),
        '{}',
        'ghost',
        'unknown', 'unknown',
        '["UCloud"]',
        '[]',
        'Unknown',
        'This job was started outside of UCloud or with an application which no longer exists.',
        null,
        $$
        {
          "vnc": null,
          "web": null,
          "tool": {
            "name": "unknown",
            "tool": null,
            "version": "unknown"
          },
          "container": {
            "runAsRoot": false,
            "runAsRealUser": false,
            "changeWorkingDirectory": true
          },
          "invocation": [
            {
              "type": "word",
              "word": "sh"
            },
            {
              "type": "word",
              "word": "-c"
            },
            {
              "type": "word",
              "word": "exit 0"
            }
          ],
          "parameters": [],
          "environment": null,
          "allowPublicIp": false,
          "allowMultiNode": true,
          "fileExtensions": [],
          "licenseServers": [],
          "allowPublicLink": false,
          "applicationType": "BATCH",
          "outputFileGlobs": [
            "*"
          ],
          "allowAdditionalPeers": null,
          "allowAdditionalMounts": null
        }
        $$
    );
-- SNIP

select 1; -- Can't end on a snip.
