create or replace function "grant".can_submit_application(
    username_in text,
    source text,
    grant_recipient text,
    grant_recipient_type text
) returns boolean language sql as $$
    with
        non_excluded_user as (
            select
                requesting_user.id, requesting_user.email, requesting_user.org_id
            from
                auth.principals requesting_user left join
                "grant".exclude_applications_from exclude_entry on
                    requesting_user.email like '%@' || exclude_entry.email_suffix and
                    exclude_entry.project_id = source
            where
                requesting_user.id = username_in
            group by
                requesting_user.id, requesting_user.email, requesting_user.org_id
            having
                count(email_suffix) = 0
        ),
        allowed_user as (
            select user_info.id
            from
                non_excluded_user user_info join
                "grant".allow_applications_from allow_entry on
                    allow_entry.project_id = source and
                    (
                        (
                            allow_entry.type = 'anyone'
                        ) or

                        (
                            allow_entry.type = 'wayf' and
                            allow_entry.applicant_id = user_info.org_id
                        ) or

                        (
                            allow_entry.type = 'email' and
                            user_info.email like '%@' || allow_entry.applicant_id
                        )
                    )
        ),

        existing_project_is_parent as (
            select existing_project.id
            from
                project.projects source_project join
                project.projects existing_project on
                    source_project.id = source and
                    source_project.id = existing_project.parent and
                    grant_recipient_type = 'existing_project' and
                    existing_project.id = grant_recipient join
                project.project_members pm on
                    pm.username = username_in and
                    pm.project_id = existing_project.id and
                    (
                        pm.role = 'ADMIN' or
                        pm.role = 'PI'
                    )
        )
    select coalesce(bool_or(allowed), false)
    from (
        select true allowed
        from
            allowed_user join
            "grant".is_enabled on
                is_enabled.project_id = source
        where allowed_user.id is not null

        union

        select true allowed
        from existing_project_is_parent
    ) t
$$;
