-- Moved to internal
drop function if exists "grant".approve_application(
    approved_by text,
    application_id_in bigint
);

drop function if exists accounting.charge(
    requests accounting.charge_request[]
);

drop function if exists accounting.process_charge_requests(
    requests accounting.charge_request[]
);

drop function if exists accounting.transfer(
    requests accounting.transfer_request[]
);

drop function if exists accounting.process_transfer_requests(
    requests accounting.transfer_request[]
);

drop function if exists accounting.credit_check(
    requests accounting.charge_request[]
);

drop function if exists accounting.credit_check(
    requests accounting.transfer_request[]
);

drop function if exists accounting.deposit(
    requests accounting.deposit_request[]
);

drop function if exists accounting.update_allocations(
    request accounting.allocation_update_request[]
);

alter table "grant".applications add column synchronized boolean default false;

update "grant".applications
set synchronized = true
where overall_state = 'APPROVED'
