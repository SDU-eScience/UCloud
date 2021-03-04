create table cooldowns(
    username text,
    project text,
    timestamp timestamp,

    primary key (username, project, timestamp)
);
