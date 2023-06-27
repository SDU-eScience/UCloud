create table auth.additional_user_info(
    associated_user int references auth.principals(uid),
    organization_full_name text default null,
    department text default null,
    research_field text default null,
    position text default null
);

alter table auth.registration add column organization_full_name text default null;
alter table auth.registration add column department text default null;
alter table auth.registration add column research_field text default null;
alter table auth.registration add column position text default null;
