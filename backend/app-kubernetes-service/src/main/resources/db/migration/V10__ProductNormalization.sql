update accounting.products p
set price_per_unit = price_per_unit / cpu
from accounting.product_categories pc
where
    pc.id = p.category and
    pc.product_type = 'COMPUTE' and
    cpu is not null;
