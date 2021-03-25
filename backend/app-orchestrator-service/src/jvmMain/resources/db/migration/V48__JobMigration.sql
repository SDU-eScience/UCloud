set search_path to app_orchestrator;

drop table if exists job_updates;
drop table if exists job_resources;
drop table if exists job_input_parameters;
drop table if exists jobs;

create table if not exists jobs
(
    id                     text      not null primary key,
    launched_by            text      not null,
    project                text,
    refresh_token          text,
    application_name       text      not null,
    application_version    text      not null,
    price_per_unit         bigint    not null,
    time_allocation_millis bigint             default null,
    credits_charged        bigint    not null default 0,
    product_provider       text      not null,
    product_category       text      not null,
    product_id             text      not null,
    replicas               int       not null default 1,
    name                   text               default null,
    output_folder          text               default null,
    last_scan              timestamp          default now(),
    current_state          text      not null,
    last_update            timestamp not null default now()
);

create table if not exists job_updates
(
    job_id text      not null references jobs (id),
    ts     timestamp not null,
    state  text default null,
    status text default null
);

create table if not exists job_input_parameters
(
    job_id text  not null references jobs (id),
    name   text  not null,
    value  jsonb not null
);

create table if not exists job_resources
(
    job_id   text  not null references jobs (id),
    resource jsonb not null
);

create or replace function migrate_license_server(obj jsonb) returns jsonb as
$$
begin
    if obj ? 'type' and obj ? 'id' and obj ? 'address' and obj ? 'port' and obj ->> 'type' = 'license_server' then
        return obj;
    end if;
    return null;
end;
$$ language plpgsql;

create or replace function migrate_file(obj jsonb) returns jsonb as
$$
declare
    output jsonb;
begin
    if obj ? 'type' and obj ->> 'type' = 'file' and obj ? 'source' then
        output = '{
          "type": "file"
        }'::jsonb;
        output = output || jsonb_build_object('path', obj ->> 'source');
        return output;
    end if;
    return null;
end;
$$ language plpgsql;

create or replace function migrate_bool(obj jsonb) returns jsonb as
$$
begin
    if obj ? 'type' and obj ->> 'type' = 'boolean' and obj ? 'value' then return obj; end if;
    return null;
end;
$$ language plpgsql;

create or replace function migrate_text(obj jsonb) returns jsonb as
$$
begin
    if obj ? 'type' and obj ->> 'type' = 'text' and obj ? 'value' then return obj; end if;
    return null;
end;
$$ language plpgsql;

create or replace function migrate_int(obj jsonb) returns jsonb as
$$
begin
    if obj ? 'type' and obj ->> 'type' = 'integer' and obj ? 'value' then return obj; end if;
    return null;
end;
$$ language plpgsql;

create or replace function migrate_floating_point(obj jsonb) returns jsonb as
$$
begin
    if obj ? 'type' and obj ->> 'type' = 'floating_point' and obj ? 'value' then return obj; end if;
    return null;
end;
$$ language plpgsql;

create or replace function migrate_enum(obj jsonb) returns jsonb as
$$
declare
    output jsonb;
begin
    if obj ? 'type' and obj ->> 'type' = 'enumeration' and obj ? 'value' then
        -- Note: This is not a one-to-one conversion, unfortunately.
        output = '{
          "type": "text"
        }'::jsonb;
        output = output || jsonb_build_object('value', obj ->> 'value');
        return output;
    end if;
    return null;
end;
$$ language plpgsql;

create or replace function migrate_peer(obj jsonb) returns jsonb as
$$
declare
    output jsonb;
begin
    if obj ? 'type' and obj ->> 'type' = 'peer' and obj ? 'peerJobId' then
        -- Note: This is not a one-to-one conversion, unfortunately.
        output = '{
          "type": "peer"
        }'::jsonb;
        output = output || jsonb_build_object('jobId', obj ->> 'peerJobId') ||
                 jsonb_build_object('hostname', obj ->> 'peerJobId');
        return output;
    end if;
    return null;
end;
$$ language plpgsql;

create or replace function jobs_data_migration() returns void as
$$
declare
    job                 app_orchestrator.job_information;
    parameter_name      text;
    parameter_value     jsonb;
    parameter_type      text;
    parameter_converted jsonb;
    file_path           text;
    peer_name           text;
    peer_id             text;
begin
    for job in ( select * from app_orchestrator.job_information )
        loop -- We start with the easy stuff. The overall metadata about the job. This should be enough that
    -- old jobs still have a valid enough record in the new system.
            insert into app_orchestrator.jobs
            values (job.system_id,
                    job.owner,
                    job.project,
                    job.refresh_token,
                    job.application_name,
                    job.application_version,
                    job.reserved_price_per_unit,
                    (job.max_time_hours::bigint * 3600 * 1000) + (job.max_time_minutes * 60 * 1000) +
                    (job.max_time_seconds * 1000),
                    coalesce(job.credits_charged, 0),
                    job.reserved_provider,
                    job.reserved_category,
                    job.reservation_type,
                    job.nodes,
                    job.name,
                    job.output_folder,
                    job.last_scan,
                    job.state,
                    job.modified_at);

            insert into app_orchestrator.job_updates
            values (job.system_id,
                    coalesce(job.created_at, now()),
                    'IN_QUEUE',
                    'In queue');

            insert into app_orchestrator.job_updates
            values (job.system_id,
                    coalesce(job.modified_at, now()),
                    'IN_QUEUE',
                    'In queue');

            insert into app_orchestrator.job_updates
            values (job.system_id,
                    coalesce(job.started_at, now()),
                    'RUNNING',
                    'Job is now running');

            insert into app_orchestrator.job_updates
            values (job.system_id,
                    job.modified_at,
                    job.state,
                    job.status);

            -- Now for the tricky part, we want to insert entries about our parameters
            for parameter_name in ( select jsonb_object_keys(job.parameters) )
                loop
                    parameter_value := job.parameters -> parameter_name;
                    parameter_type := parameter_value ->> 'type';
                    parameter_converted := null;

                    if parameter_type = 'license_server' then
                        select migrate_license_server(parameter_value) into parameter_converted;
                    elsif parameter_type = 'file' then
                        select migrate_file(parameter_value) into parameter_converted;
                    elsif parameter_type = 'boolean' then
                        select migrate_bool(parameter_value) into parameter_converted;
                    elsif parameter_type = 'text' then
                        select migrate_text(parameter_value) into parameter_converted;
                    elsif parameter_type = 'integer' then
                        select migrate_int(parameter_value) into parameter_converted;
                    elsif parameter_type = 'floating_point' then
                        select migrate_floating_point(parameter_value) into parameter_converted;
                    elsif parameter_type = 'enumeration' then
                        select migrate_enum(parameter_value) into parameter_converted;
                    elsif parameter_type = 'peer' then
                        select migrate_peer(parameter_value) into parameter_converted;
                    end if;

                    if parameter_converted is not null then
                        insert into app_orchestrator.job_input_parameters
                        values (job.system_id, parameter_name, parameter_converted);
                    end if;
                end loop;

            for parameter_value in ( select jsonb_array_elements(job.mounts) )
                loop
                    select parameter_value -> 'stat' ->> 'path' into file_path;

                    if file_path is not null then
                        insert into app_orchestrator.job_resources
                        values (job.system_id,
                                jsonb_build_object('type', 'file', 'path', file_path, 'readOnly', true));
                    end if;
                end loop;

            for parameter_value in ( select jsonb_array_elements(job.peers) )
                loop
                    select parameter_value ->> 'name' into peer_name;
                    select parameter_value ->> 'jobId' into peer_id;

                    if peer_name is not null and peer_id is not null then
                        insert into app_orchestrator.job_resources
                        values (job.system_id,
                                jsonb_build_object('type', 'peer', 'hostname', peer_name, 'jobId', peer_id));
                    end if;
                end loop;

            if job.url is not null then
                insert into app_orchestrator.job_resources
                values (job.system_id, jsonb_build_object('type', 'ingress', 'domain', job.url));
            end if;
        end loop;
end;
$$ language plpgsql;

select app_orchestrator.jobs_data_migration();

drop function if exists app_orchestrator.jobs_data_migration;
drop function if exists app_orchestrator.migrate_bool;
drop function if exists app_orchestrator.migrate_enum;
drop function if exists app_orchestrator.migrate_file;
drop function if exists app_orchestrator.migrate_floating_point;
drop function if exists app_orchestrator.migrate_int;
drop function if exists app_orchestrator.migrate_license_server;
drop function if exists app_orchestrator.migrate_peer;
drop function if exists app_orchestrator.migrate_text;

update app_orchestrator.jobs set current_state = 'FAILURE' where current_state = 'RUNNING';
