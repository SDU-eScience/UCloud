create table sessions(
    job_id text not null,
    rank int not null,
    session_id text not null,
    type text not null,
    expires_at timestamp not null,
    primary key (session_id)
);
