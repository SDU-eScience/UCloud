create table app_orchestrator.ssh_keys(
    id bigserial not null primary key,
    owner text not null references auth.principals(id),
    created_at timestamptz default now(),
    title text not null,
    key text not null
);

create index ssh_keys_owner on app_orchestrator.ssh_keys (owner);
