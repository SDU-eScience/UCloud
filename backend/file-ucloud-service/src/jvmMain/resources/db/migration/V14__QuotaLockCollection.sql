delete from file_ucloud.quota_locked where true;
alter table file_ucloud.quota_locked drop column collection;
alter table file_ucloud.quota_locked add column category text not null default '';
alter table file_ucloud.quota_locked add column username text default null;
alter table file_ucloud.quota_locked add column project_id text default null;
