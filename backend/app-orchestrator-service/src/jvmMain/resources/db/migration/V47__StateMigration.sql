update job_information
set state = 'IN_QUEUE'
where
      state = 'VALIDATED' or
      state = 'PREPARED' or
      state = 'SCHEDULED';

update job_information
set state = 'SUCCESS'
where state = 'TRANSFER_SUCCESS';

update job_information
set failed_state = 'IN_QUEUE'
where
        failed_state = 'VALIDATED' or
        failed_state = 'PREPARED' or
        failed_state = 'SCHEDULED';

update job_information
set failed_state = 'SUCCESS'
where failed_state = 'TRANSFER_SUCCESS';