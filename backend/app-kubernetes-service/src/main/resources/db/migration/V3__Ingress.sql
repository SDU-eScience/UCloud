drop table if exists app_kubernetes.ingresses;
drop table if exists app_kubernetes.bound_ingress;
create table app_kubernetes.ingresses
(
    id     text not null primary key,
    domain text not null unique
);
create table app_kubernetes.bound_ingress
(
    ingress_id text not null primary key references app_kubernetes.ingresses (id),
    job_id     text not null
);
