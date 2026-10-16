alter table public.custom_design_templates
drop constraint if exists custom_design_templates_rows_check;

alter table public.custom_design_templates
add constraint custom_design_templates_rows_check
    check (
        jsonb_typeof(rows_json) = 'object'
        and (
            (
                (rows_json - array['1', '2', '3', '4', '5', '6', '7', '8', '9', '10', '11', '12']) = '{}'::jsonb
                and rows_json ?& array['1', '2', '3', '4', '5', '6', '7', '8', '9', '10', '11', '12']
                and case
                    when jsonb_typeof(rows_json -> '1') = 'number'
                    and jsonb_typeof(rows_json -> '2') = 'number'
                    and jsonb_typeof(rows_json -> '3') = 'number'
                    and jsonb_typeof(rows_json -> '4') = 'number'
                    and jsonb_typeof(rows_json -> '5') = 'number'
                    and jsonb_typeof(rows_json -> '6') = 'number'
                    and jsonb_typeof(rows_json -> '7') = 'number'
                    and jsonb_typeof(rows_json -> '8') = 'number'
                    and jsonb_typeof(rows_json -> '9') = 'number'
                    and jsonb_typeof(rows_json -> '10') = 'number'
                    and jsonb_typeof(rows_json -> '11') = 'number'
                    and jsonb_typeof(rows_json -> '12') = 'number'
                    then
                    (rows_json ->> '1')::numeric between 0 and source_height
                    and (rows_json ->> '2')::numeric between 0 and source_height
                    and (rows_json ->> '3')::numeric between 0 and source_height
                    and (rows_json ->> '4')::numeric between 0 and source_height
                    and (rows_json ->> '5')::numeric between 0 and source_height
                    and (rows_json ->> '6')::numeric between 0 and source_height
                    and (rows_json ->> '7')::numeric between 0 and source_height
                    and (rows_json ->> '8')::numeric between 0 and source_height
                    and (rows_json ->> '9')::numeric between 0 and source_height
                    and (rows_json ->> '10')::numeric between 0 and source_height
                    and (rows_json ->> '11')::numeric between 0 and source_height
                    and (rows_json ->> '12')::numeric between 0 and source_height
                    and (rows_json ->> '1')::numeric < (rows_json ->> '2')::numeric
                    and (rows_json ->> '2')::numeric < (rows_json ->> '3')::numeric
                    and (rows_json ->> '3')::numeric < (rows_json ->> '4')::numeric
                    and (rows_json ->> '4')::numeric < (rows_json ->> '5')::numeric
                    and (rows_json ->> '5')::numeric < (rows_json ->> '6')::numeric
                    and (rows_json ->> '6')::numeric < (rows_json ->> '7')::numeric
                    and (rows_json ->> '7')::numeric < (rows_json ->> '8')::numeric
                    and (rows_json ->> '8')::numeric < (rows_json ->> '9')::numeric
                    and (rows_json ->> '9')::numeric < (rows_json ->> '10')::numeric
                    and (rows_json ->> '10')::numeric < (rows_json ->> '11')::numeric
                    and (rows_json ->> '11')::numeric < (rows_json ->> '12')::numeric
                    else false
                end
            )
            or
            (
                (rows_json - array['1', '2', '3', '4', '5', '6', '7', '8', '9', '10', '11', '12', '13', '14', '15', '16', '17', '18']) = '{}'::jsonb
                and rows_json ?& array['1', '2', '3', '4', '5', '6', '7', '8', '9', '10', '11', '12', '13', '14', '15', '16', '17', '18']
                and case
                    when jsonb_typeof(rows_json -> '1') = 'number'
                    and jsonb_typeof(rows_json -> '2') = 'number'
                    and jsonb_typeof(rows_json -> '3') = 'number'
                    and jsonb_typeof(rows_json -> '4') = 'number'
                    and jsonb_typeof(rows_json -> '5') = 'number'
                    and jsonb_typeof(rows_json -> '6') = 'number'
                    and jsonb_typeof(rows_json -> '7') = 'number'
                    and jsonb_typeof(rows_json -> '8') = 'number'
                    and jsonb_typeof(rows_json -> '9') = 'number'
                    and jsonb_typeof(rows_json -> '10') = 'number'
                    and jsonb_typeof(rows_json -> '11') = 'number'
                    and jsonb_typeof(rows_json -> '12') = 'number'
                    and jsonb_typeof(rows_json -> '13') = 'number'
                    and jsonb_typeof(rows_json -> '14') = 'number'
                    and jsonb_typeof(rows_json -> '15') = 'number'
                    and jsonb_typeof(rows_json -> '16') = 'number'
                    and jsonb_typeof(rows_json -> '17') = 'number'
                    and jsonb_typeof(rows_json -> '18') = 'number'
                    then
                    (rows_json ->> '1')::numeric between 0 and source_height
                    and (rows_json ->> '2')::numeric between 0 and source_height
                    and (rows_json ->> '3')::numeric between 0 and source_height
                    and (rows_json ->> '4')::numeric between 0 and source_height
                    and (rows_json ->> '5')::numeric between 0 and source_height
                    and (rows_json ->> '6')::numeric between 0 and source_height
                    and (rows_json ->> '7')::numeric between 0 and source_height
                    and (rows_json ->> '8')::numeric between 0 and source_height
                    and (rows_json ->> '9')::numeric between 0 and source_height
                    and (rows_json ->> '10')::numeric between 0 and source_height
                    and (rows_json ->> '11')::numeric between 0 and source_height
                    and (rows_json ->> '12')::numeric between 0 and source_height
                    and (rows_json ->> '13')::numeric between 0 and source_height
                    and (rows_json ->> '14')::numeric between 0 and source_height
                    and (rows_json ->> '15')::numeric between 0 and source_height
                    and (rows_json ->> '16')::numeric between 0 and source_height
                    and (rows_json ->> '17')::numeric between 0 and source_height
                    and (rows_json ->> '18')::numeric between 0 and source_height
                    and (rows_json ->> '1')::numeric < (rows_json ->> '2')::numeric
                    and (rows_json ->> '2')::numeric < (rows_json ->> '3')::numeric
                    and (rows_json ->> '3')::numeric < (rows_json ->> '4')::numeric
                    and (rows_json ->> '4')::numeric < (rows_json ->> '5')::numeric
                    and (rows_json ->> '5')::numeric < (rows_json ->> '6')::numeric
                    and (rows_json ->> '6')::numeric < (rows_json ->> '7')::numeric
                    and (rows_json ->> '7')::numeric < (rows_json ->> '8')::numeric
                    and (rows_json ->> '8')::numeric < (rows_json ->> '9')::numeric
                    and (rows_json ->> '9')::numeric < (rows_json ->> '10')::numeric
                    and (rows_json ->> '10')::numeric < (rows_json ->> '11')::numeric
                    and (rows_json ->> '11')::numeric < (rows_json ->> '12')::numeric
                    and (rows_json ->> '12')::numeric < (rows_json ->> '13')::numeric
                    and (rows_json ->> '13')::numeric < (rows_json ->> '14')::numeric
                    and (rows_json ->> '14')::numeric < (rows_json ->> '15')::numeric
                    and (rows_json ->> '15')::numeric < (rows_json ->> '16')::numeric
                    and (rows_json ->> '16')::numeric < (rows_json ->> '17')::numeric
                    and (rows_json ->> '17')::numeric < (rows_json ->> '18')::numeric
                    else false
                end
            )
            or
            (
                (rows_json - array['1', '2', '3', '4', '5', '6', '7', '8', '9', '10', '11', '12', '13', '14', '15', '16', '17', '18', '19', '20', '21', '22', '23', '24']) = '{}'::jsonb
                and rows_json ?& array['1', '2', '3', '4', '5', '6', '7', '8', '9', '10', '11', '12', '13', '14', '15', '16', '17', '18', '19', '20', '21', '22', '23', '24']
                and case
                    when jsonb_typeof(rows_json -> '1') = 'number'
                    and jsonb_typeof(rows_json -> '2') = 'number'
                    and jsonb_typeof(rows_json -> '3') = 'number'
                    and jsonb_typeof(rows_json -> '4') = 'number'
                    and jsonb_typeof(rows_json -> '5') = 'number'
                    and jsonb_typeof(rows_json -> '6') = 'number'
                    and jsonb_typeof(rows_json -> '7') = 'number'
                    and jsonb_typeof(rows_json -> '8') = 'number'
                    and jsonb_typeof(rows_json -> '9') = 'number'
                    and jsonb_typeof(rows_json -> '10') = 'number'
                    and jsonb_typeof(rows_json -> '11') = 'number'
                    and jsonb_typeof(rows_json -> '12') = 'number'
                    and jsonb_typeof(rows_json -> '13') = 'number'
                    and jsonb_typeof(rows_json -> '14') = 'number'
                    and jsonb_typeof(rows_json -> '15') = 'number'
                    and jsonb_typeof(rows_json -> '16') = 'number'
                    and jsonb_typeof(rows_json -> '17') = 'number'
                    and jsonb_typeof(rows_json -> '18') = 'number'
                    and jsonb_typeof(rows_json -> '19') = 'number'
                    and jsonb_typeof(rows_json -> '20') = 'number'
                    and jsonb_typeof(rows_json -> '21') = 'number'
                    and jsonb_typeof(rows_json -> '22') = 'number'
                    and jsonb_typeof(rows_json -> '23') = 'number'
                    and jsonb_typeof(rows_json -> '24') = 'number'
                    then
                    (rows_json ->> '1')::numeric between 0 and source_height
                    and (rows_json ->> '2')::numeric between 0 and source_height
                    and (rows_json ->> '3')::numeric between 0 and source_height
                    and (rows_json ->> '4')::numeric between 0 and source_height
                    and (rows_json ->> '5')::numeric between 0 and source_height
                    and (rows_json ->> '6')::numeric between 0 and source_height
                    and (rows_json ->> '7')::numeric between 0 and source_height
                    and (rows_json ->> '8')::numeric between 0 and source_height
                    and (rows_json ->> '9')::numeric between 0 and source_height
                    and (rows_json ->> '10')::numeric between 0 and source_height
                    and (rows_json ->> '11')::numeric between 0 and source_height
                    and (rows_json ->> '12')::numeric between 0 and source_height
                    and (rows_json ->> '13')::numeric between 0 and source_height
                    and (rows_json ->> '14')::numeric between 0 and source_height
                    and (rows_json ->> '15')::numeric between 0 and source_height
                    and (rows_json ->> '16')::numeric between 0 and source_height
                    and (rows_json ->> '17')::numeric between 0 and source_height
                    and (rows_json ->> '18')::numeric between 0 and source_height
                    and (rows_json ->> '19')::numeric between 0 and source_height
                    and (rows_json ->> '20')::numeric between 0 and source_height
                    and (rows_json ->> '21')::numeric between 0 and source_height
                    and (rows_json ->> '22')::numeric between 0 and source_height
                    and (rows_json ->> '23')::numeric between 0 and source_height
                    and (rows_json ->> '24')::numeric between 0 and source_height
                    and (rows_json ->> '1')::numeric < (rows_json ->> '2')::numeric
                    and (rows_json ->> '2')::numeric < (rows_json ->> '3')::numeric
                    and (rows_json ->> '3')::numeric < (rows_json ->> '4')::numeric
                    and (rows_json ->> '4')::numeric < (rows_json ->> '5')::numeric
                    and (rows_json ->> '5')::numeric < (rows_json ->> '6')::numeric
                    and (rows_json ->> '6')::numeric < (rows_json ->> '7')::numeric
                    and (rows_json ->> '7')::numeric < (rows_json ->> '8')::numeric
                    and (rows_json ->> '8')::numeric < (rows_json ->> '9')::numeric
                    and (rows_json ->> '9')::numeric < (rows_json ->> '10')::numeric
                    and (rows_json ->> '10')::numeric < (rows_json ->> '11')::numeric
                    and (rows_json ->> '11')::numeric < (rows_json ->> '12')::numeric
                    and (rows_json ->> '12')::numeric < (rows_json ->> '13')::numeric
                    and (rows_json ->> '13')::numeric < (rows_json ->> '14')::numeric
                    and (rows_json ->> '14')::numeric < (rows_json ->> '15')::numeric
                    and (rows_json ->> '15')::numeric < (rows_json ->> '16')::numeric
                    and (rows_json ->> '16')::numeric < (rows_json ->> '17')::numeric
                    and (rows_json ->> '17')::numeric < (rows_json ->> '18')::numeric
                    and (rows_json ->> '18')::numeric < (rows_json ->> '19')::numeric
                    and (rows_json ->> '19')::numeric < (rows_json ->> '20')::numeric
                    and (rows_json ->> '20')::numeric < (rows_json ->> '21')::numeric
                    and (rows_json ->> '21')::numeric < (rows_json ->> '22')::numeric
                    and (rows_json ->> '22')::numeric < (rows_json ->> '23')::numeric
                    and (rows_json ->> '23')::numeric < (rows_json ->> '24')::numeric
                    else false
                end
            )
        )
    );
