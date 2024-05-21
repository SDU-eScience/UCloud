package dk.sdu.cloud.sql.migrations

import dk.sdu.cloud.sql.MigrationScript
import dk.sdu.cloud.sql.useAndInvokeAndDiscard

fun V1__UCloudCompute() = MigrationScript("V1__UCloudCompute") { session ->
    session.prepareStatement(
        //language=postgresql
        """
            create table ucloud_compute_ingresses(
                id text not null primary key,
                domain text not null unique
            );
        """
    ).useAndInvokeAndDiscard()

    session.prepareStatement(
        //language=postgresql
        """
            create table ucloud_compute_bound_ingress(
            	ingress_id text not null primary key references ucloud_compute_ingresses,
            	job_id text not null
            );
        """
    ).useAndInvokeAndDiscard()

    session.prepareStatement(
        //language=postgresql
        """
            create table ucloud_compute_network_ip_pool(
            	external_cidr text not null primary key,
            	internal_cidr text not null unique
            );
        """
    ).useAndInvokeAndDiscard()

    session.prepareStatement(
        //language=postgresql
        """
           create table ucloud_compute_network_ips(
                id text not null primary key,
                external_ip_address text not null,
                internal_ip_address text not null,
                owner text default '' not null
            );
        """
    ).useAndInvokeAndDiscard()

    session.prepareStatement(
        //language=postgresql
        """
            create table ucloud_compute_bound_network_ips(
            	network_ip_id text not null primary key references ucloud_compute_network_ips,
            	job_id text not null
            );
        """
    ).useAndInvokeAndDiscard()

    session.prepareStatement(
        //language=postgresql
        """
            create table ucloud_compute_sessions(
            	job_id text not null,
            	rank int not null,
            	session_id text not null primary key,
            	type text not null,
            	expires_at timestamp not null
            );
        """
    ).useAndInvokeAndDiscard()

    session.prepareStatement(
        //language=postgresql
        """
            create table ucloud_compute_cluster_state(
                cluster_id text not null primary key,
                paused boolean
            );
        """
    ).useAndInvokeAndDiscard()

    session.prepareStatement(
        //language=postgresql
        """
            create table ucloud_compute_license_servers(
            	id text not null primary key,
            	address text not null,
            	port int not null,
            	license text,
            	tags text,
            	price_per_unit bigint default 1000000 not null,
            	description text default '' not null,
            	product_availability text default '',
            	priority int default 0 not null,
            	payment_model text default 'PER_ACTIVATION' not null
            );
        """
    ).useAndInvokeAndDiscard()

    session.prepareStatement(
        //language=postgresql
        """
            create table ucloud_compute_license_instances(
                orchestrator_id text not null primary key,
                server_id text not null references ucloud_compute_license_servers
            );
        """
    ).useAndInvokeAndDiscard()
}

fun V2__UCloudCompute() = MigrationScript("V2__UCloudCompute") { session ->
    session.prepareStatement(
        //language=postgresql
        """
            create table ucloud_compute_bound_ssh_ports(
                name text not null,
                subnet text not null,
                port int not null,
                job_id text not null,
                primary key(name, subnet, port)
            );
        """
    ).useAndInvokeAndDiscard()
}

fun V3__UCloudCompute() = MigrationScript("V3__UCloudCompute") { session ->
    session.prepareStatement(
        //language=postgresql
        """
            alter table ucloud_compute_ingresses add column owner text default null
        """
    ).useAndInvokeAndDiscard()
}
