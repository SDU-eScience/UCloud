CREATE INDEX application_file_extensions ON applications USING GIN ((application -> 'fileExtensions') JSONB_PATH_OPS);
