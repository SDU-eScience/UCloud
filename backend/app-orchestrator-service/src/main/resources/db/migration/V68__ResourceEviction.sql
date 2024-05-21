-- Resources
-- ====================================================================================================================
create function provider.notify_resource_updated() returns trigger language plpgsql as $$
begin
    perform pg_notify('resource_eviction', new.type || '-' || coalesce(p.pid, 0) || '-' || u.uid)
    from
        auth.principals u
        left join project.projects p on new.project = p.id
    where
        u.id = new.created_by;
    return new;
end;
$$;

create function provider.notify_resource_deleted() returns trigger language plpgsql as $$
begin
    perform pg_notify('resource_eviction', old.type || '-' || coalesce(p.pid, 0) || '-' || u.uid)
    from
        auth.principals u
        left join project.projects p on new.project = p.id
    where
        u.id = old.created_by;
    return old;
end;
$$;

create trigger resource_trigger before insert or update on provider.resource for each row when (current_setting('ucloud.performed_by_cache', true) is distinct from 'true')
execute function provider.notify_resource_updated();

create trigger resource_trigger_delete before delete on provider.resource for each row when (current_setting('ucloud.performed_by_cache', true) is distinct from 'true')
execute function provider.notify_resource_deleted();

-- ACL
-- ====================================================================================================================
create function provider.notify_resource_acl_updated() returns trigger language plpgsql as $$
begin
    perform pg_notify('resource_eviction', r.type || '-' || coalesce(p.pid, 0) || '-' || u.uid)
    from
        provider.resource r
        join auth.principals u on u.id = r.created_by
        left join project.projects p on r.project = p.id
    where
        r.id = new.resource_id;
    return new;
end;
$$;

create function provider.notify_resource_acl_deleted() returns trigger language plpgsql as $$
begin
    perform pg_notify('resource_eviction', r.type || '-' || coalesce(p.pid, 0) || '-' || u.uid)
    from
        provider.resource r
        join auth.principals u on u.id = r.created_by
        left join project.projects p on r.project = p.id
    where
        r.id = old.resource_id;
    return old;
end;
$$;

create trigger resource_trigger before insert or update on provider.resource_acl_entry for each row when (current_setting('ucloud.performed_by_cache', true) is distinct from 'true')
execute function provider.notify_resource_acl_updated();

create trigger resource_trigger_delete before delete on provider.resource_acl_entry for each row when (current_setting('ucloud.performed_by_cache', true) is distinct from 'true')
execute function provider.notify_resource_acl_deleted();

-- Updates
-- ====================================================================================================================
create function provider.notify_resource_update_updated() returns trigger language plpgsql as $$
begin
    perform pg_notify('resource_eviction', r.type || '-' || coalesce(p.pid, 0) || '-' || u.uid)
    from
        provider.resource r
        join auth.principals u on u.id = r.created_by
        left join project.projects p on r.project = p.id
    where
        r.id = new.resource;
    return new;
end;
$$;

create function provider.notify_resource_update_deleted() returns trigger language plpgsql as $$
begin
    perform pg_notify('resource_eviction', r.type || '-' || coalesce(p.pid, 0) || '-' || u.uid)
    from
        provider.resource r
        join auth.principals u on u.id = r.created_by
        left join project.projects p on r.project = p.id
    where
        r.id = old.resource;
    return old;
end;
$$;

create trigger resource_trigger before insert or update on provider.resource_update for each row when (current_setting('ucloud.performed_by_cache', true) is distinct from 'true')
execute function provider.notify_resource_update_updated();

create trigger resource_trigger_delete before delete on provider.resource_update for each row when (current_setting('ucloud.performed_by_cache', true) is distinct from 'true')
execute function provider.notify_resource_update_deleted();
