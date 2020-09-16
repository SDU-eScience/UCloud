create sequence application_sequence start 1 increment 1;

create table address_applications
(
  id                bigint       not null,
  created_at        timestamp    not null,
  accepted_at       timestamp    default null,
  released_at       timestamp    default null,
  ip                text         default null,
  status            varchar(255) not null,
  applicant_id      varchar(255) not null,
  applicant_type    varchar(255) not null,
  application       text         not null,
  primary key (id)
);

create table ip_pool (
  ip          text not null,
  owner_id    text,
  owner_type  text,
  primary key (ip)
);

create table open_ports (
  ip      text not null,
  port    integer not null check (port >= 0 and port <= 65535),
  primary key (ip, port)
);

create or replace function allocate_or_release_ip() returns trigger as $$
begin
    case when new.status = 'ACCEPTED' then
        UPDATE ip_pool set owner_id = new.applicant_id, owner_type = new.applicant_type where ip = new.ip
    case when new.status = 'RELEASED' then
        UPDATE ip_pool set owner_id = null, owner_type = null where ip = new.ip
    end;
end;
$$ language plpgsql;

create trigger trigger_accept_reject_application
    after update on address_applications
    for each row
    execute procedure allocate_or_release_ip();