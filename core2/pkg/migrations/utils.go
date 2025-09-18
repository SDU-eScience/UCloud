package migrations

import db "ucloud.dk/shared/pkg/database2"

func utilsV1() db.MigrationScript {
	return db.MigrationScript{
		Id: "utilsV1",
		Execute: func(tx *db.Transaction) {
			db.Exec(
				tx,
				`
					create or replace function public.unnest_zero_to_null(input anyarray)
					returns setof anyelement
					language sql
					strict
					immutable
					as $$
					  select
						case
						  -- empty string -> NULL for text-like types
						  when pg_typeof(x) in ('text'::regtype, 'varchar'::regtype, 'bpchar'::regtype)
							   and x::text = '' then null::anyelement

						  -- numeric zero -> NULL for int/float/numeric
						  when pg_typeof(x) in ('int2'::regtype, 'int4'::regtype, 'int8'::regtype,
												'float4'::regtype, 'float8'::regtype, 'numeric'::regtype)
							   and x::numeric = 0 then null::anyelement

						  else x
						end
					  from unnest(input) as t(x);
					$$;
			    `,
				db.Params{},
			)
		},
	}
}
