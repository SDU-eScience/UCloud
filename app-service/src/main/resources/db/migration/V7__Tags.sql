set search_path to app;

create table tags(
    name varchar(255) not null,
    version varchar(250) not null
    tag varchar(255)
    foreign key (name, version) references applications(name, version)
);