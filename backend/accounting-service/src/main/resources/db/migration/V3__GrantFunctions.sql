create or replace function project.is_admin(
    username_in text,
    project_id_in text
) returns boolean as $$
    select exists(
        select 1
        from project.project_members
        where
            username = username_in and
            (role = 'PI' or role = 'ADMIN') and
            project_id = project_id_in
    );
$$ language sql;

create or replace function project.is_admin_of_parent(
    username_in text,
    project_id_in text
) returns boolean as $$
    select exists(
        select 1
        from
            project.projects child join
            project.projects parent on parent.id = child.parent join
            project.project_members members on parent.id = members.project_id
        where
            members.username = username_in and
            child.id = project_id_in and
            (role = 'PI' or role = 'ADMIN')
    );
$$ language sql;

create or replace function project.view_ancestors(
    project_id_in text
) returns text[] as $$
declare
    current_id text;
    result text[];
    r record;
begin
    current_id = project_id_in;
    while current_id is not null loop
        select id, title, parent
        into r
        from project.projects
        where id = current_id
        limit 1;

        if r.id is not null then
            result = result || array[r.id::text, r.title::text];
        end if;

        current_id = r.parent;
    end loop;
    return result;
end;
$$ language plpgsql;

create or replace function "grant".my_applications(
    username_in text,
    include_incoming boolean,
    include_outgoing boolean
) returns setof "grant".applications as $$
begin
    return query (
        select apps.*
        from
            "grant".applications apps join
            project.projects p on apps.resources_owned_by = p.id join
            project.project_members pm on p.id = pm.project_id
        where
            (
                (include_outgoing and apps.requested_by = username_in) or
                (
                    include_incoming and
                    (pm.username = username_in and (pm.role = 'PI' or pm.role = 'ADMIN'))
                )
            )
    );
end;
$$ language plpgsql;

create type "grant".transfer_output as (
    source_title text,
    destination_title text,
    recipient_title text,
    user_to_notify text
);

create or replace function "grant".grant_recipient_title(
    grant_recipient text,
    grant_recipient_type text
) returns text as $$
declare
    result text;
begin
    result = grant_recipient;
    if grant_recipient_type = 'existing_project' then
        select title from project.projects where id = grant_recipient into result;
    end if;
    return result;
end;
$$ language plpgsql;

create or replace function "grant".transfer_application(
    actor text,
    application_id bigint,
    target_project text
) returns setof "grant".transfer_output as $$
declare
    affected_application record;
    update_count int;
begin
    select resources_owned_by, grant_recipient, grant_recipient_type into affected_application
    from "grant".applications where id = application_id;

    update "grant".applications
    set resources_owned_by = target_project
    where
        id in (select id from "grant".my_applications(actor, true, false) where id = application_id) and
        grant_recipient_type != 'existing_project' and
        status = 'IN_PROGRESS'
    returning 1 into update_count;

    if update_count is null then
        raise exception 'Unable to transfer application (Not found or permission denied)';
    end if;

    if target_project = affected_application.resources_owned_by then
        raise exception 'Unable to transfer application to itself';
    end if;

    return query (
        with source_project as (
            select title
            from project.projects
            where id = affected_application.resources_owned_by
        ),
        grant_recipient as (
            select "grant".grant_recipient_title(
                affected_application.grant_recipient,
                affected_application.grant_recipient_type
            ) as title
        ),
        target_admins as (
            select p.title, pm.username
            from
                project.projects p join
                project.project_members pm on p.id = pm.project_id
            where
                p.id = target_project and
                (pm.role = 'ADMIN' or pm.role = 'PI')
        )
        select
            source_project.title::text as source_title,
            target_admins.title::text as destination_title,
            grant_recipient.title::text as recipient_title,
            target_admins.username::text as user_to_notify
        from source_project, grant_recipient, target_admins
    );
end;
$$ language plpgsql;
