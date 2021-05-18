drop table if exists file_orchestrator.shares;
create table if not exists file_orchestrator.shares
(
    path        text not null,
    shared_by   text not null,
    shared_with text not null,
    approved    bool not null,
    primary key (path, shared_with)
);
