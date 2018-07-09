set search_path to app;

create index jobs_owner_index on jobs using hash(owner);
create index slurm_id_index on jobs(slurm_id);