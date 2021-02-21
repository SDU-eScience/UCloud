set search_path to auth;

UPDATE principals
SET dtype = 'WAYF'
WHERE dtype LIKE 'PersonEntityByWAYF';


UPDATE principals
SET dtype = 'SERVICE'
WHERE dtype LIKE 'ServiceEntity';


UPDATE principals
SET dtype = 'PASSWORD'
WHERE dtype = 'PersonEntityByPassword';

UPDATE two_factor_challenges
SET dtype = 'SETUP'
WHERE dtype LIKE '%Setup';


UPDATE two_factor_challenges
SET dtype = 'LOGIN'
WHERE dtype LIKE '%Login';
