alter table accounting_compute.job_completed_events
    add column machine_reservation_cpu int default null;
alter table accounting_compute.job_completed_events
    add column machine_reservation_gpu int default null;
alter table accounting_compute.job_completed_events
    add column machine_reservation_mem int default null;
alter table accounting_compute.job_completed_events
    add column machine_reservation_name text default null;
alter table accounting_compute.job_completed_events
    add column project_id text default null;
