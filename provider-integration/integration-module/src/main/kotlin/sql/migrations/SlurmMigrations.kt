package dk.sdu.cloud.sql.migrations

import dk.sdu.cloud.sql.*

fun V1__JobMapping(): MigrationScript = MigrationScript("V1__JobMapping") { conn ->
    conn.prepareStatement(
        //language=postgresql
        """
            create table job_mapping(
                ucloud_id text primary key,
                local_id text not null,
                partition text not null,
                status boolean default true not null,
                lastknown text not null,
                ts timestamp default now() not null
            )
        """
    ).useAndInvokeAndDiscard()
}

// NOTE(Dan): Name intentionally wrong
fun V2__SessionMapping(): MigrationScript = MigrationScript("V1__SessionMapping") { conn ->
    conn.prepareStatement(
        //TODO: using not unique token for now, this is related to 2 tokens being generated and some logic in the openInteractiveSession
        // token text primary key,

        // language=postgresql
        """
            create table session_mapping(
                pkey serial primary key,
                token text not null,
                rank int not null,
                ucloud_id text not null,
                ts timestamp default now() not null
            )
        """
    ).useAndInvokeAndDiscard()
}

// NOTE(Dan): Name intentionally wrong
fun V3__SlurmAccountMapper(): MigrationScript = MigrationScript("V1__SlurmAccountMapper") { conn ->
    conn.prepareStatement(
        //language=postgresql
        """
            create table slurm_account_mapper(
                username text,
                project_id text,
                category text not null,
                partition text not null
            )
        """
    ).useAndInvokeAndDiscard()
}

fun V4__SlurmAccounting(): MigrationScript = MigrationScript("V4__SlurmAccounting") { conn ->
    conn.prepareStatement(
        //language=postgresql
        """
            alter table job_mapping add column elapsed bigint not null default 0
        """
    ).useAndInvokeAndDiscard()
}

fun V5__FixSlurmAccountMapper(): MigrationScript = MigrationScript("V5__FixSlurmAccountMapper") { conn ->
    conn.prepareStatement(
        //language=postgresql
        """
            alter table slurm_account_mapper add column slurm_account text not null
        """
    ).useAndInvokeAndDiscard()
}
