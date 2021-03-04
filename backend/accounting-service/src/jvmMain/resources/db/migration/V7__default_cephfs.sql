-- This is required for storage-service to work in the current (16/06/2020) setup
insert into product_categories values ('ucloud', 'cephfs', 'STORAGE') on conflict do nothing;
insert into products values ('ucloud', 'cephfs', 'STORAGE', 'u1-cephfs', 0, '', null, 0, null, null, null) on conflict do nothing;
