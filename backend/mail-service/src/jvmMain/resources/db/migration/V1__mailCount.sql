set search_path to mail;
create table mail_counting(
    mail_count bigint not null,
    username text not null,
    period_start timestamp not null,
    alerted_for bool not null default false,
    primary key (username)
)
