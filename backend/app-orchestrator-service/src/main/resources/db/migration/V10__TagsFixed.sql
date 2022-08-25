drop table if exists application_tags;
drop table if exists favorited_by;

create table application_tags
(
  id                  bigint not null,
  tag                 varchar(255),
  application_name    varchar(255),
  application_version varchar(255),
  primary key (id)
);

create table applications_application_tags
(
  application_entity_name    varchar(255) not null,
  application_entity_version varchar(255) not null,
  tags_id                    bigint       not null
);

create table favorited_by
(
  id                  bigint not null,
  "user"              varchar(255),
  application_name    varchar(255),
  application_version varchar(255),
  primary key (id)
);

alter table applications_application_tags
  add constraint UK_as05q2j0jur0jp3mlugvl49l4 unique (tags_id);

alter table application_tags
  add constraint FKh4plhuqnsfykb7nslaendhm2a
    foreign key (application_name, application_version)
      references applications;

alter table applications_application_tags
  add constraint FKak590jvoc8tisjf7h66mk5gea
    foreign key (application_entity_name, application_entity_version)
      references applications;

alter table favorited_by
  add constraint FKdr4vaq58mgr0nkk0xoq41ljgo
    foreign key (application_name, application_version)
      references applications;
