alter table app_orchestrator.jobs add column started_at timestamp default null;
alter table app_orchestrator.jobs add column initial_started_at timestamp default null;

create or replace function app_orchestrator.update_state() returns trigger as
$$
begin
    update app_orchestrator.jobs
    set
        current_state = coalesce(new.state, current_state),
        last_update = new.ts,
        started_at =
            case (new.state)
                when 'RUNNING' then greatest(new.ts, jobs.started_at)
                else jobs.started_at
                end,
        initial_started_at =
            case (new.state)
                when 'RUNNING' then least(new.ts, jobs.initial_started_at)
                else jobs.initial_started_at
                end
    where
            id = new.job_id and
            new.ts >= last_update;
    return null;
end;
$$ language plpgsql;

drop trigger if exists update_state on app_orchestrator.job_updates;
create trigger update_state
    after insert
    on app_orchestrator.job_updates
    for each row
execute procedure app_orchestrator.update_state();
