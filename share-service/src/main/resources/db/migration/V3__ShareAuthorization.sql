create or replace function is_share_authorized(share shares, auth_user text, auth_role text)
    RETURNS bool as
$func$
begin
    return (
        (auth_user is null || auth_role is null) or (auth_role = 'OWNER' and share."owner" = auth_user) or
        (auth_role = 'RECIPIENT' and (share.shared_with = auth_user)) or
        (auth_role = 'PARTICIPANT' and (share."owner" = auth_user or share.shared_with = auth_user))
    );
end;
$func$ LANGUAGE plpgsql;

create or replace function has_share_relation(share shares, username text, shared_by_me bool)
    returns bool as
$func$
begin
    return (
            (shared_by_me and share.owner = username) or
            (not shared_by_me and share.shared_with = username)
        );
end;
$func$ language plpgsql;
