insert into accounting.products (name, price_per_unit, cpu, gpu, memory_in_gigs, license_tags, category, free_to_use, description)
select 'share', 1, null, null, null, null, pc.id, false, 'Shares for UCloud (personal workspaces only)'
from accounting.product_categories pc where pc.provider = 'ucloud' and pc.category = 'u1-cephfs'
on conflict do nothing;
