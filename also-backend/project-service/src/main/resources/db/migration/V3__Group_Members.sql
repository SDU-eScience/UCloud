create table group_members(
    project varchar(2048) not null,
    the_group varchar(2048) not null,
    username varchar(2048) not null,
    primary key (project, the_group, username),
    foreign key (project, the_group) references groups(project, the_group)
);
