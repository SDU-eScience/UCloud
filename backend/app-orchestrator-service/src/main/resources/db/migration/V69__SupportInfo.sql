create table app_orchestrator.machine_support_info(
    product_name text not null,
    product_category text not null,
    product_provider text not null,
    backend_type text not null,

    vnc bool not null,
    logs bool not null,
    terminal bool not null,
    time_extension bool not null,
    web bool not null,
    peers bool not null,
    suspend bool not null,

    primary key (product_name, product_category, product_provider)
);
