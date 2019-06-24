UPDATE tools
SET tool = tool - 'defaultAllocationTime' || jsonb_build_object('defaultTimeAllocation', tool -> 'defaultAllocationTime')
where tool ? 'defaultAllocationTime';
