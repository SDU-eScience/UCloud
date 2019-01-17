set search_path to project;

create sequence hibernate_sequence start 1 increment 1;

create table project_members
(
  id          int8 not null,
  created_at  timestamp,
  modified_at timestamp,
  role        varchar(255),
  username    varchar(255),
  project_id  varchar(255),
  primary key (id)
);

create table projects
(
  id          varchar(255) not null,
  created_at  timestamp,
  modified_at timestamp,
  title       varchar(4096),
  primary key (id)
);

alter table if exists project_members
  add constraint UKdy62i2lqtu30g25bay7yt8o17 unique (username, project_id);

alter table if exists project_members
  add constraint FKdki1sp2homqsdcvqm9yrix31g
    foreign key (project_id)
      references project.projects;
