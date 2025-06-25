package reporting

import (
	"bytes"
	"encoding/gob"
	"time"
	db "ucloud.dk/shared/pkg/database"
)

func fetchAndDumpWallets(provider string, start, end time.Time) []byte {
	samples := db.NewTx(func(tx *db.Transaction) []reportingSample {
		return db.Select[reportingSample](
			tx,
			`
				select
					cast(extract(epoch from s.sampled_at) * 1000 as int8) as timestamp, 
					s.wallet_id,
					s.quota,
					s.local_usage,
					s.tree_usage,
					to_jsonb(s.tree_usage_by_group) as usage_by_group,
					p.id as project,
					pc.category,
					pc.provider
				from
					accounting.wallet_samples_v2 s
					join accounting.wallets_v2 w on s.wallet_id = w.id
					join accounting.wallet_owner wo on w.wallet_owner = wo.id
					join project.projects p on wo.project_id = p.id
					join accounting.product_categories pc on w.product_category = pc.id
				where
					pc.provider = :provider
					and s.sampled_at >= to_timestamp(:start / 1000.0)
					and s.sampled_at <= to_timestamp(:end / 1000.0)
		    `,
			db.Params{
				"provider": provider,
				"start":    start.UnixMilli(),
				"end":      end.UnixMilli(),
			},
		)
	})

	b := &bytes.Buffer{}
	err := gob.NewEncoder(b).Encode(samples)
	if err != nil {
		panic(err)
	}

	return b.Bytes()
}

func fetchAndDumpAllocations(provider string, start, end time.Time) []byte {
	allocations := db.NewTx(func(tx *db.Transaction) []allocation {
		return db.Select[allocation](
			tx,
			`
				select
					alloc.id,
					alloc.allocation_start_time as start,
					alloc.allocation_end_time as "end",
					w.id as wallet_id,
					alloc.quota,
					coalesce(ag.parent_wallet, 0) as parent,
					pc.category,
					pc.provider,
					p.id as project
				from
					accounting.wallet_allocations_v2 alloc
					join accounting.allocation_groups ag on alloc.associated_allocation_group = ag.id
					join accounting.wallets_v2 w on ag.associated_wallet = w.id
					join accounting.product_categories pc on w.product_category = pc.id
					join accounting.wallet_owner wo on w.wallet_owner = wo.id
					join project.projects p on wo.project_id = p.id
				where
					pc.provider = :provider
					and alloc.allocation_start_time <= to_timestamp(:end / 1000.0)
					and to_timestamp(:start / 1000.0) <= allocation_end_time
		    `,
			db.Params{
				"provider": provider,
				"start":    start.UnixMilli(),
				"end":      end.UnixMilli(),
			},
		)
	})

	b := &bytes.Buffer{}
	err := gob.NewEncoder(b).Encode(allocations)
	if err != nil {
		panic(err)
	}

	return b.Bytes()
}

func fetchAndDumpMembers(provider string, start, end time.Time) []byte {
	members := db.NewTx(func(tx *db.Transaction) []projectMember {
		return db.Select[projectMember](
			tx,
			`
				with
					job_launches as (
						select
							pm.username as user_id,
							pm.project_id as project,
							count(r.id) as job_runs
						from
							project.project_members pm
							left join provider.resource r on pm.project_id = r.project and pm.username = r.created_by
							left join accounting.products p on r.product = p.id
							left join accounting.product_categories pc on p.category = pc.id
						where
							r.type = 'job'
							and r.created_at >= to_timestamp(:start / 1000.0)
							and r.created_at <= to_timestamp(:end / 1000.0)
							and pc.provider = :provider
						group by pm.username, pm.project_id
					)
				select distinct 
					pm.username as user_id, 
					pm.project_id as project, 
					coalesce(jl.job_runs, 0) as job_runs
				from
					project.project_members pm
					join project.projects p on pm.project_id = p.id
					join accounting.wallet_owner wo on p.id = wo.project_id
					join accounting.wallets_v2 w on wo.id = w.wallet_owner
					join accounting.allocation_groups ag on w.id = ag.associated_wallet
					join accounting.wallet_allocations_v2 alloc on ag.id = alloc.associated_allocation_group
					join accounting.product_categories pc on w.product_category = pc.id
					left join job_launches jl on jl.user_id = pm.username and jl.project = pm.project_id
				where
					pc.provider = :provider;
		    `,
			db.Params{
				"provider": provider,
				"start":    start.UnixMilli(),
				"end":      end.UnixMilli(),
			},
		)
	})

	b := &bytes.Buffer{}
	err := gob.NewEncoder(b).Encode(members)
	if err != nil {
		panic(err)
	}

	return b.Bytes()
}
