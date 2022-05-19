alter table "grant".requested_resources add source_allocation bigint;
alter table "grant".requested_resources add start_date timestamp default null;
alter table "grant".requested_resources add end_date timestamp;
alter table "grant".requested_resources add grant_giver text references project.projects(id);

--MIGRATE resources_owned_by to requested_resources
update "grant".requested_resources rr
set grant_giver = a.resources_owned_by
from "grant".applications a
where rr.application_id = a.id;

create table "grant".grant_giver_approvals(
    application_id bigint references "grant".applications(id),
    project_id text,
    project_title text,
    state text,
    updated_by text,
    last_update timestamp
);

create table "grant".revisions(
    application_id bigint references "grant".applications(id),
    created_at timestamp,
    updated_by text not null,
    revision_number int not null default 0,
    revision_comment text,
    PRIMARY KEY (application_id, revision_number)
);

alter table "grant".requested_resources add column revision_number int;
alter table "grant".requested_resources add foreign key (application_id,revision_number) references "grant".revisions(application_id, revision_number);

create table "grant".forms(
    application_id bigint references "grant".applications(id),
    revision_number int,
    recipient text,
    recipient_type text,
    form text,
    reference_id text,
    FOREIGN KEY (application_id, revision_number) references "grant".revisions(application_id, revision_number)
);

-- Migrate applications to new schemas
with old_applications as (
    select *
    from "grant".applications
),
revisions as (
    insert into "grant".revisions (application_id, created_at, updated_by, revision_number, revision_comment)
    select oa.id, oa.created_at, oa.status_changed_by, 0
    from old_applications oa
    returning application_id, revision_number
),
applications_with_revisions as (
    select revision_number, grant_recipient, grant_recipient_type, document, reference_id
    from "grant".applications join "grant".revisions r on applications.id = r.application_id
)
insert into "grant".documents (revision_number, recipient, recipient_type, form, reference_id, revision_comment)
select awr.revision_number, awr.grant_recipient, awr.grant_recipient_type, awr.document, awr.reference_id, 'no comment'
from applications_with_revisions awr;

-- Migrate approval states
with old_applications as (
    select a.id app_id, p.id pro_id, p.title, a.status, a.status_changed_by, a.updated_at
    from "grant".applications a join project.projects p on a.resources_owned_by = p.id
)
insert into "grant".grant_giver_approvals(application_id, project_id, project_title, state, updated_by, last_update)
select * from old_applications;

-- DELETE cleanup application table
alter table "grant".applications drop column resources_owned_by;
alter table "grant".applications drop column grant_recipient;
alter table "grant".applications drop column grant_recipient_type;
alter table "grant".applications drop column document;
alter table "grant".applications drop column reference_id;
alter table "grant".applications drop column status_changed_by;
alter table "grant".applications drop column updated_at;
-- rename columns to match new struct
alter table "grant".applications rename status to overall_state;


