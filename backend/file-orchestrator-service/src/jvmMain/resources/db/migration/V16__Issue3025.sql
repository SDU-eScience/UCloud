update file_orchestrator.metadata_templates
set
    schema = '{"type": "object", "title": "UCloud: File Sensitivity", "required": ["sensitivity"], "properties": {"sensitivity": {"enum": ["SENSITIVE", "CONFIDENTIAL", "PRIVATE"], "type": "string", "title": "File Sensitivity", "enumNames": ["Sensitive", "Confidential", "Private"]}}, "dependencies": {}}'::jsonb,
    inheritable = true
where title = 'UCloud File Sensitivity';

update file_orchestrator.metadata_templates
set
    schema = '{"type": "object", "title": "UCloud: Favorite Files", "required": ["favorite"], "properties": {"favorite": {"type": "boolean", "title": "Is this file on of your favorites?"}}, "description": "A document describing your favorite files", "dependencies": {}}'::jsonb,
    inheritable = false
where title = 'Favorite';
