drop table if exists app_kubernetes.bound_network_ips;
drop table if exists app_kubernetes.network_ip_pool;
drop table if exists app_kubernetes.network_ips;

create table app_kubernetes.network_ips
(
    id         text not null primary key,
    external_ip_address text not null,
    internal_ip_address text not null
);

create table app_kubernetes.network_ip_pool
(
    external_cidr text not null primary key,
    internal_cidr text not null unique
);

create table app_kubernetes.bound_network_ips
(
    network_ip_id text not null primary key references app_kubernetes.network_ips (id),
    job_id        text not null
);
