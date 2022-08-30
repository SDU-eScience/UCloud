delete from app_kubernetes.bound_network_ips where true;

update app_kubernetes.network_ips ip
set id = r.id
from provider.resource r
where r.provider_generated_id = ip.id;

alter table app_kubernetes.network_ips add column owner text not null default '';

update app_kubernetes.network_ips ip
set owner = coalesce(r.project, r.created_by)
from provider.resource r
where ip.id::bigint = r.id;

drop trigger if exists require_immutable_product_category on accounting.product_categories;

update accounting.product_categories pc
set
    charge_type = 'DIFFERENTIAL_QUOTA',
    unit_of_price = 'PER_UNIT'
where
    pc.product_type = 'NETWORK_IP';

create trigger require_immutable_product_category
after update of charge_type, product_type on accounting.product_categories
for each row execute procedure accounting.require_immutable_product_category();
