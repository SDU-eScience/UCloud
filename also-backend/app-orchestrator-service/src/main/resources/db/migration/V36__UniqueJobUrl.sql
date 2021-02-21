create unique index job_information_url on job_information (url);
alter table job_information add constraint unique_job_url unique using index job_information_url;
