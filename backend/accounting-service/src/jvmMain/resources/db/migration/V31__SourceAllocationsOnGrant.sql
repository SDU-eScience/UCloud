alter table "grant".requested_resources add source_allocation bigint;
alter table "grant".requested_resources add start_date timestamp default null;
alter table "grant".requested_resources add end_date timestamp;
alter table "grant".requested_resources add grant_giver text references project.projects(id);
alter table "grant".requested_resources add revision_number int references "grant".revision(revision_number) default 0;

--MIGRATE resources_owned_by to requested_resources
update "grant".requested_resources rr
set grant_giver = a.resources_owned_by
from "grant".applications a
where rr.application_id = a.id;

create table "grant".grant_giver_approval(
    application_id bigint references "grant".applications(id),
    project_id text,
    project_title text,
    state text,
    updated_by text,
    last_update timestamp
);

create table "grant".revision(
    application_id bigint references "grant".applications(id),
    created_at timestamp default to_timestamp(now()),
    updated_by text not null,
    revision_number int not null default 0,
    PRIMARY KEY (application_id, revision_number)
);

create table "grant".document(
    application_id bigint references "grant".applications(id),
    revision_number int references "grant".revision(revision_number) unique,
    recipient text,
    recipient_type text,
    form text,
    reference_id text,
    revision_comment text,
    PRIMARY KEY (application_id, revision_number)
);

-- Migrate applications to new schemas
with old_applications as (
    select *
    from "grant".applications
),
revisions as (
    insert into "grant".revision (application_id, created_at, updated_by, revision_number)
    VALUES (old_applications.id, old_applications.created_at, old_applications.status_changed_by, 0)
    returning application_id, revision_number
),
applications_with_revisions as (
    select *
    from "grant".applications join "grant".revision r on applications.id = r.application_id
)
insert into "grant".document (revision_number, recipient, recipient_type, form, reference_id, revision_comment)
VALUES (
    applications_with_revisions.revision_number,
    applications_with_revisions.grant_recipient,
    applications_with_revisions.grant_recipient_type,
    applications_with_revisions.document,
    applications_with_revisions.reference_id,
    'no comment'
);

-- Migrate approval states
with old_applications as (
    select a.id app_id, p.id pro_id, p.title, a.status, a.status_changed_by, a.updated_at
    from "grant".applications a join project.projects p on a.resources_owned_by = p.id
)
insert into grant_giver_approval (application_id, project_id, project_title, state, updated_by, last_update)
values (
    old_applications.app_id,
    old_applications.pro_id,
    old_applications.title,
    old_applications.status,
    old_applications.status_changed_by,
    old_applications.updated_at
);

-- DELETE cleanup application table
alter table "grant".applications drop column resources_owned_by;
alter table "grant".applications drop column grant_recipient;
alter table "grant".applications drop column grant_recipient_type;
alter table "grant".applications drop column document;
alter table "grant".applications drop column reference_id;
alter table "grant".applications drop column status_changed_by;
-- rename columns to match new struct
alter table "grant".applications rename status to overall_state;


