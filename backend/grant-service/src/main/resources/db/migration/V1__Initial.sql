create sequence application_id start 1;

create table applications(
    status text not null,
    resources_owned_by text not null,
    requested_by text not null,
    grant_recipient text not null,
    grant_recipient_type text not null,
    document text not null,
    id int primary key default nextval('application_id')
);

create table requested_resources(
    application_id int not null,
    product_category text not null,
    product_provider text not null,
    credits_requested bigint default null,
    quota_requested_bytes bigint default null,
    foreign key (application_id) references applications(id)
);

create sequence comment_id start 1;

create table comments(
    application_id int not null,
    comment text not null,
    posted_by text not null,
    created_at timestamp not null default now(),
    id int primary key default nextval('comment_id'),
    foreign key (application_id) references applications(id)
);

create table allow_applications_from(
    project_id text not null,
    type text not null,
    applicant_id text default null,
    primary key (project_id, type, applicant_id)
);

create table automatic_approval(
    project_id text not null,
    type text not null,
    applicant_id text default null,
    product_category text not null,
    product_provider text not null,
    maximum_credits bigint default null,
    maximum_quota_bytes bigint default null
);

create table templates(
    project_id text not null primary key,
    personal_project text not null,
    existing_project text not null,
    new_project text not null
);
