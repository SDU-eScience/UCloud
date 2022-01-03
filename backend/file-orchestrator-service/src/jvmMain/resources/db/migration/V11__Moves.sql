create or replace function file_orchestrator.array_drop_last(
    count int,
    arr anyarray
) returns anyarray language sql as $$
    select array(
        select arr[i]
        from generate_series(
            array_lower(arr, 1),
            array_upper(arr, 1) - count
        ) as s(i)
        order by i
    )
$$ strict immutable;

create or replace function file_orchestrator.parent_file(
    file_path text
) returns text language sql as $$
    select array_to_string(file_orchestrator.array_drop_last(1, regexp_split_to_array(file_path, '/')), '/');
$$ strict immutable;
