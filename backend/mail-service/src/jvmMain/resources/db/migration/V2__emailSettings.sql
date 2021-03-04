CREATE TABLE email_settings(
    username varchar(255) UNIQUE NOT NULL,
    settings jsonb NOT NULL
);
