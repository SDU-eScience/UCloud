do
$BODY$
    declare
        _con text := (
            select quote_ident(conname)
            from pg_constraint
            where conrelid = 'app_store.application_tags'::regclass
              and confrelid = 'app_store.applications'::regclass
            limit 1
        );

    begin
        execute 'alter table app_store.application_tags drop constraint ' || _con;
    end;
$BODY$;

alter table app_store.application_tags drop column application_version;
