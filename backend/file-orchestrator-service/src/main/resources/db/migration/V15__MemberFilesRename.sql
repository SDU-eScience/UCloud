update file_orchestrator.file_collections
set title = 'Member Files: ' || (regexp_split_to_array(title, 'Member File: '))[2]
where title like 'Member File: %';
