create table app_store.workflows(
    id                  bigserial primary key,
    created_at          timestamptz not null default now(),
    modified_at         timestamptz not null default now(),
    created_by          text not null references auth.principals(id),
    project_id          text references project.projects(id),
    application_name    text not null,
    language            text not null,
    is_open             bool not null,
    path                text not null,
    init                text,
    job                 text,
    inputs              jsonb
);

create index on app_store.workflows (application_name, coalesce(project_id, created_by));
create unique index on app_store.workflows (coalesce(project_id, created_by), path);

create table app_store.workflow_permissions(
    workflow_id     int8 references app_store.workflows(id),
    group_id        text not null references project.groups(id),
    permission      text not null,

    unique (workflow_id, group_id)
);
