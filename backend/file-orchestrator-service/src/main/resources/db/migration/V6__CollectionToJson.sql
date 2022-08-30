create or replace function file_orchestrator.file_collection_to_json(
    collection_in file_orchestrator.file_collections
) returns jsonb as $$
    select jsonb_build_object(
        'specification', jsonb_build_object(
            'title', collection_in.title
        )
    );
$$ language sql;
