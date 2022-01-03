drop table if exists file_orchestrator.task_mapping;
create table if not exists file_orchestrator.task_mapping(
    provider_id text references provider.providers(unique_name),
    provider_generated_task_id varchar(255) not null,
    actual_task_id text not null references task.tasks(job_id)
);
