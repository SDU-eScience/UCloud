ALTER TABLE project.projects
    ADD CONSTRAINT ensure_root_title_unique
        EXCLUDE (title WITH =) WHERE (parent IS null);