alter table job_information add column reservation_type varchar(255) default 'MachineType';
alter table job_information add column reserved_gpus int default null;
