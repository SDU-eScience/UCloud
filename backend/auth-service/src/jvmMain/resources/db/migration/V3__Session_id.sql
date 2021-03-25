-- Introduces a unique session reference to the refresh_tokens table. This reference can uniquely identify a refresh
-- token, without needing it to be secret. This makes it suitable for embedding in a JWT.
--
-- It is important to note that this session reference is never used for other purposes than identifying
-- the originating session. It is not proof of owning a session.
set search_path to auth;

alter table refresh_tokens add column public_session_reference VARCHAR (256);
alter table refresh_tokens add unique (public_session_reference);