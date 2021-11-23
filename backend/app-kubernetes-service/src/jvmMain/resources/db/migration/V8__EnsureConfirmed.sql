-- Fixes #2852
update provider.resource set confirmed_by_provider = true where true;
