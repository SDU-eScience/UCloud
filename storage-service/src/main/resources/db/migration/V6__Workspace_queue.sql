create table workspace_jobs
(
    workspace_id text not null,
    file_globs text not null,
    destination text not null,
    replace_existing boolean not null,
    delete boolean not null,
    status int not null,
    primary key (workspace_id)
);
