drop table if exists file_orchestrator.metadata_template_specs;
drop table if exists file_orchestrator.metadata_templates;

create table file_orchestrator.metadata_templates
(
    id             text      not null primary key,
    latest_version text,
    created_by     text      not null,
    project        text,
    acl            jsonb     not null,
    updates        jsonb     not null,
    namespace_type text      not null,
    is_public      bool      not null default false,
    deprecated     bool      not null default false,
    created_at     timestamp not null default now(),
    modified_at    timestamp not null default now()
);

create table file_orchestrator.metadata_template_specs
(
    template_id      text      not null references file_orchestrator.metadata_templates (id),
    title            text      not null,
    version          text      not null,
    schema           jsonb     not null,
    inheritable      bool      not null,
    require_approval bool      not null,
    description      text      not null,
    change_log       text      not null,
    namespace_type   text      not null,
    ui_schema        jsonb              default null,
    created_at       timestamp not null default now(),
    unique(template_id, version)
);


insert into file_orchestrator.metadata_templates
values ('favorite',
        '1',
        '_UCluod',
        null,
        '[]'::jsonb,
        '[]'::jsonb,
        'PER_USER',
        true,
        false,
        now(),
        now());

insert into file_orchestrator.metadata_template_specs
values ('favorite',
        'Favorite',
        '1',
        '{
          "type": "object",
          "title": "Favorite",
          "properties": {
            "favorite": {
              "title": "Is this one of your favorite files?",
              "type": "boolean",
              "description": ""
            }
          },
          "dependencies": {},
          "required": []
        }',
        false,
        false,
        'Favorite status of a file',
        'N/A',
        'PER_USER',
        '{
          "ui:order": [
            "favorite"
          ]
        }',
        now());
