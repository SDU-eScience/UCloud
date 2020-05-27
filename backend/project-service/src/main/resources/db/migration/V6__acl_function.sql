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
                gm.the_group = group_filter
        )
    ) into passed;

    RETURN passed;
END;
$$  LANGUAGE plpgsql