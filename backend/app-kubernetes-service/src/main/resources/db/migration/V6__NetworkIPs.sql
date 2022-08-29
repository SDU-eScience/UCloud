drop table if exists app_kubernetes.network_ips;
drop table if exists app_kubernetes.network_ip_pool;
drop table if exists app_kubernetes.bound_network_ips;

create table app_kubernetes.network_ips
(
    id         text not null primary key,
    ip_address text not null
);

create table app_kubernetes.network_ip_pool
(
    cidr text not null primary key
);

create table app_kubernetes.bound_network_ips
(
    network_ip_id text not null primary key references app_kubernetes.network_ips (id),
    job_id        text not null
);
