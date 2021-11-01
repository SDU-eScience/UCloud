create type file_orchestrator.share_state as enum ('APPROVED', 'PENDING', 'REJECTED');

drop table if exists file_orchestrator.shares;
create table file_orchestrator.shares(
    resource bigint references provider.resource(id) primary key,
    shared_with text not null references auth.principals(id),
    permissions text[] not null,
    original_file_path text not null,
    available_at text default null,
    state file_orchestrator.share_state default 'PENDING'::file_orchestrator.share_state
);

create unique index on file_orchestrator.shares (original_file_path, shared_with);

create or replace function file_orchestrator.share_to_json(
    share_in file_orchestrator.shares
) returns jsonb language sql as $$
    select jsonb_build_object(
        'specification', jsonb_build_object(
            'sourceFilePath', share_in.original_file_path,
            'sharedWith', share_in.shared_with,
            'permissions', share_in.permissions
        ),
        'status', jsonb_build_object(
            'shareAvailableAt', share_in.available_at,
            'state', share_in.state
        )
    )
$$;
