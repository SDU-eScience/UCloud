create table provider.connected_with(
    username text references auth.principals(id),
    provider_id text references provider.providers(id),
    primary key (username, provider_id)
);
