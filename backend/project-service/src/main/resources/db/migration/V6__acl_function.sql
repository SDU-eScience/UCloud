CREATE OR REPLACE FUNCTION check_group_acl(uname text, is_admin boolean, g groups)
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
                gm.the_group = g.the_group
        )
    ) into passed;

    RETURN passed;
END;
$$  LANGUAGE plpgsql