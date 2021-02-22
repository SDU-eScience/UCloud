set search_path to auth;

create table auth.cursor_state (
      id varchar(255) not null,
      expires_at timestamp,
      hostname varchar(255),
      port int4 not null,
      primary key (id)
);
