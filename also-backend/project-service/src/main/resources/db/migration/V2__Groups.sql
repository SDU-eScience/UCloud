create table groups
(
    "the_group"   varchar(255),
    "project" varchar(255),
    primary key ("the_group", "project"),
    foreign key ("project") references projects (id)
);
