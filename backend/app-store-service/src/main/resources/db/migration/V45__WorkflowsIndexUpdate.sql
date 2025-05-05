drop index app_store.workflows_coalesce_path_idx;

create unique index workflows_coalesce_path_idx
    on app_store.workflows (COALESCE(project_id, created_by), application_name, path);