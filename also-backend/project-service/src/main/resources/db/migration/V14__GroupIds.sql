alter table groups
    add column id varchar(255)
        default uuid_in(md5(random()::text || clock_timestamp()::text)::cstring)::text
        not null;

alter table group_members add column group_id varchar(255) null;

update group_members set group_id = (select id from groups where
    group_members.the_group=groups.the_group and group_members.project=groups.project);

alter table group_members alter column group_id set not null;

alter table group_members drop constraint group_members_pkey;
alter table group_members drop constraint group_members_project_members_fkey;
alter table group_members drop constraint group_members_project_the_group_fkey;
alter table group_members drop constraint if exists group_members_project_fkey;

alter table groups drop constraint groups_pkey;
alter table groups add primary key (id);
alter table groups rename column the_group to title;

alter table group_members add primary key (group_id, username);
alter table group_members add constraint group_members_group_fkey foreign key (group_id) references groups (id);

alter table group_members drop column the_group;
alter table group_members drop column project;

CREATE OR REPLACE FUNCTION check_group_acl(uname text, is_admin boolean, group_filter text)
RETURNS BOOLEAN AS $$
DECLARE passed BOOLEAN;
BEGIN
    select (
        is_admin or
        uname in (
            select gm.username
            from group_members gm
            where
                gm.username = uname and
                gm.group_id = group_filter
        )
    ) into passed;

    RETURN passed;
END;
$$  LANGUAGE plpgsql