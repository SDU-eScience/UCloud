create table file_orchestrator.collections(
    resource bigint references provider.resource(id) primary key,
    title varchar(4096) not null check (length(title) > 0)
);

create or replace function file_orchestrator.collection_to_json(
    collection_in file_orchestrator.collections
) returns jsonb as $$
    select jsonb_build_object(
        'title', collection_in.title
    );
$$ language sql;

create unique index on file_orchestrator.collections (resource);
