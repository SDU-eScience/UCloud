UPDATE tools
SET tool = tool - 'defaultMaxTime' || jsonb_build_object('defaultAllocationTime', tool -> 'defaultMaxTime')
where tool ? 'defaultMaxTime';

UPDATE tools
SET tool = jsonb_set(tool, '{backend}', '"DOCKER"')
where tool ->> 'backend' = '"UDOCKER"';


UPDATE tools
set tool = jsonb_set(tool, '{license}', '""')
