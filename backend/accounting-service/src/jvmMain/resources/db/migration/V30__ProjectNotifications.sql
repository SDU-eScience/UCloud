create table project.provider_notifications(
	id bigserial,
	provider_id text references provider.providers (unique_name),
	project_id text references project.projects,
	created_at timestamp with time zone default now() not null
);
