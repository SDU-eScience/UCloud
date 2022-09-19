alter table app_orchestrator.ssh_keys add column if not exists fingerprint text not null default 'Fingerprint unavailable';
