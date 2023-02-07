create table if not exists file_orchestrator.shares_links(
    token uuid primary key not null,
    file_path text not null,
    expires timestamptz not null,
    permissions text not null default 'READ'
);