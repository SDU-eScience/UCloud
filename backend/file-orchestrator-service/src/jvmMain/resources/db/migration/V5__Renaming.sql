alter table file_orchestrator.collections rename to file_collections;

alter function file_orchestrator.collection_to_json(collection_in file_orchestrator.file_collections) rename to file_collection_to_json;
