--ONLY SEEN ON LOCAL AND DEV SYSTEMS (Same three apps: Ubuntu 0.20.0 and 0.20.0-dev and matlab r2022b-dev)
delete
from app_store.applications
where application->'vnc'->>'port' = '0' or application->'web'->>'port' = '0';
