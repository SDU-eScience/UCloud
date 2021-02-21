create index metadata_path on storage.metadata using btree(path TEXT_PATTERN_OPS);
create index metadata_username on storage.metadata (username);
create index metadata_type on storage.metadata (type);
