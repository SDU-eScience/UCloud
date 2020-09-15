create sequence application_sequence start 1 increment 1;

create table address_applications
(
  id                uuid         not null,
  created_at        timestamp    not null,
  status            varchar(255) not null,
  applicant_id      varchar(255) not null,
  applicant_type    varchar(255) not null,
  application       text         not null,
  primary key (id)
);
