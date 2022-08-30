update "grant".gift_resources
set
    credits = quota / (1024 * 1024 * 1024),
    quota = null
where
    product_category in (
        select id from accounting.product_categories where product_type = 'STORAGE'
    );
