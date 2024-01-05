drop function "grant".create_gift(
    actor_in text,
    gift_resources_owned_by_in text,
    title_in text,
    description_in text,

    criteria_type_in text[],
    criteria_entity_in text[],

    resource_cat_name_in text[],
    resource_provider_name_in text[],
    resources_credits_in bigint[],
    resources_quota_in bigint[]
);

alter table "grant".gifts add column renewal_policy text default 'NEVER';

create or replace function "grant".create_gift(
    actor_in text,
    gift_resources_owned_by_in text,
    title_in text,
    description_in text,
    renewal text,

    criteria_type_in text[],
    criteria_entity_in text[],

    resource_cat_name_in text[],
    resource_provider_name_in text[],
    resources_credits_in bigint[],
    resources_quota_in bigint[]
) returns bigint language plpgsql as $$
declare
    can_create_gift boolean := false;
    created_gift_id bigint;
begin
    select count(*) > 0 into can_create_gift
    from
        project.project_members pm
    where
        pm.project_id = gift_resources_owned_by_in and
        pm.username = actor_in and
        (pm.role = 'ADMIN' or pm.role = 'PI');

    if not can_create_gift then
        raise exception 'Unable to create a gift in this project. Are you an admin?';
    end if;

    insert into "grant".gifts (resources_owned_by, title, description, renewal_policy)
    values (gift_resources_owned_by_in, title_in, description_in, renewal)
    returning id into created_gift_id;

    insert into "grant".gifts_user_criteria (gift_id, type, applicant_id)
    select created_gift_id, unnest(criteria_type_in), unnest(criteria_entity_in);

    insert into "grant".gift_resources (gift_id, credits, quota, product_category)
    with entries as (
        select unnest(resource_cat_name_in) category, unnest(resource_provider_name_in) provider,
               unnest(resources_quota_in) quota, unnest(resources_credits_in) credits
    )
    select created_gift_id, e.credits, e.quota, pc.id
    from
        entries e join
        accounting.product_categories pc on
            e.category = pc.category and
            e.provider = pc.provider;

    return created_gift_id;
end;
$$;
