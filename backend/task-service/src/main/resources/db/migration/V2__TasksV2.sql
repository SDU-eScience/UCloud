create table task.tasks_v2(
   id bigserial primary key ,
   created_at timestamptz not null default now(),
   modified_at timestamptz not null default now(),
   created_by text not null references auth.principals(id),
   owned_by text not null references provider.providers(unique_name),
   state text not null default 'IN_QUEUE',
   operation text default null,
   progress text default null,
   can_pause bool not null default false,
   can_cancel bool not null default false
)
