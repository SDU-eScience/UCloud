set search_path to auth;

create table two_factor_challenges (
  dtype          varchar(31)  not null,
  challenge_id   varchar(255) not null,
  expires_at     timestamp    not null,
  credentials_id bigint       not null,
  service        varchar(255),
  primary key (challenge_id)
);

create table two_factor_credentials (
  id            bigint       not null,
  enforced      boolean      not null,
  shared_secret varchar(255) not null,
  principal_id  varchar(255) not null,
  primary key (id)
);

alter table two_factor_challenges
  add constraint FK49sh77iu9qiv6u8on743bqcpj
foreign key (credentials_id)
references two_factor_credentials;

alter table two_factor_credentials
  add constraint FK2icqj501wstw3udr36ewytrpq
foreign key (principal_id)
references principals;