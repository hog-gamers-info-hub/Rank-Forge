alter table public.custom_design_templates
    drop constraint if exists custom_design_templates_labels_check,
    drop constraint if exists custom_design_templates_columns_check,
    drop constraint if exists custom_design_templates_text_colors_check;

alter table public.custom_design_templates
    add constraint custom_design_templates_labels_check
    check (
        jsonb_typeof(labels_json) = 'object'
        and labels_json ? 'teamName'
        and labels_json ? 'win'
        and labels_json ? 'totalKills'
        and labels_json ? 'positionPoints'
        and labels_json ? 'totalPoints'
        and (
            (
                not labels_json ? 'matchesPlayed'
                and (labels_json - 'teamName' - 'win' - 'totalKills' -
                    'positionPoints' - 'totalPoints') = '{}'::jsonb
            )
            or (
                labels_json ? 'matchesPlayed'
                and (labels_json - 'teamName' - 'win' - 'totalKills' -
                    'positionPoints' - 'totalPoints' - 'matchesPlayed') = '{}'::jsonb
                and jsonb_typeof(labels_json -> 'matchesPlayed') = 'string'
                and btrim(labels_json ->> 'matchesPlayed') <> ''
            )
        )
        and jsonb_typeof(labels_json -> 'teamName') = 'string'
        and btrim(labels_json ->> 'teamName') <> ''
        and jsonb_typeof(labels_json -> 'win') = 'string'
        and btrim(labels_json ->> 'win') <> ''
        and jsonb_typeof(labels_json -> 'totalKills') = 'string'
        and btrim(labels_json ->> 'totalKills') <> ''
        and jsonb_typeof(labels_json -> 'positionPoints') = 'string'
        and btrim(labels_json ->> 'positionPoints') <> ''
        and jsonb_typeof(labels_json -> 'totalPoints') = 'string'
        and btrim(labels_json ->> 'totalPoints') <> ''
    ),
    add constraint custom_design_templates_columns_check
    check (
        jsonb_typeof(columns_json) = 'object'
        and columns_json ? 'TEAM_NAME'
        and columns_json ? 'WIN'
        and columns_json ? 'TOTAL_KILLS'
        and columns_json ? 'POSITION_POINTS'
        and columns_json ? 'TOTAL_POINTS'
        and (
            (
                not labels_json ? 'matchesPlayed'
                and (columns_json - 'TEAM_NAME' - 'WIN' - 'TOTAL_KILLS' -
                    'POSITION_POINTS' - 'TOTAL_POINTS') = '{}'::jsonb
                and case
                    when jsonb_typeof(columns_json -> 'TEAM_NAME') = 'number'
                        and jsonb_typeof(columns_json -> 'WIN') = 'number'
                        and jsonb_typeof(columns_json -> 'TOTAL_KILLS') = 'number'
                        and jsonb_typeof(columns_json -> 'POSITION_POINTS') = 'number'
                        and jsonb_typeof(columns_json -> 'TOTAL_POINTS') = 'number'
                    then (columns_json ->> 'TEAM_NAME')::numeric between 0 and source_width
                        and (columns_json ->> 'WIN')::numeric between 0 and source_width
                        and (columns_json ->> 'TOTAL_KILLS')::numeric between 0 and source_width
                        and (columns_json ->> 'POSITION_POINTS')::numeric between 0 and source_width
                        and (columns_json ->> 'TOTAL_POINTS')::numeric between 0 and source_width
                    else false
                end
            )
            or (
                labels_json ? 'matchesPlayed'
                and columns_json ? 'MATCHES_PLAYED'
                and (columns_json - 'TEAM_NAME' - 'WIN' - 'MATCHES_PLAYED' -
                    'TOTAL_KILLS' - 'POSITION_POINTS' - 'TOTAL_POINTS') = '{}'::jsonb
                and case
                    when jsonb_typeof(columns_json -> 'TEAM_NAME') = 'number'
                        and jsonb_typeof(columns_json -> 'WIN') = 'number'
                        and jsonb_typeof(columns_json -> 'MATCHES_PLAYED') = 'number'
                        and jsonb_typeof(columns_json -> 'TOTAL_KILLS') = 'number'
                        and jsonb_typeof(columns_json -> 'POSITION_POINTS') = 'number'
                        and jsonb_typeof(columns_json -> 'TOTAL_POINTS') = 'number'
                    then (columns_json ->> 'TEAM_NAME')::numeric between 0 and source_width
                        and (columns_json ->> 'WIN')::numeric between 0 and source_width
                        and (columns_json ->> 'MATCHES_PLAYED')::numeric between 0 and source_width
                        and (columns_json ->> 'TOTAL_KILLS')::numeric between 0 and source_width
                        and (columns_json ->> 'POSITION_POINTS')::numeric between 0 and source_width
                        and (columns_json ->> 'TOTAL_POINTS')::numeric between 0 and source_width
                    else false
                end
            )
        )
    ),
    add constraint custom_design_templates_text_colors_check
    check (
        (
            not labels_json ? 'matchesPlayed'
            and (
                text_colors_json is null
                or (
                    jsonb_typeof(text_colors_json) = 'object'
                    and (text_colors_json - 'TEAM_NAME' - 'WIN' - 'TOTAL_KILLS' -
                        'POSITION_POINTS' - 'TOTAL_POINTS') = '{}'::jsonb
                    and jsonb_typeof(text_colors_json -> 'TEAM_NAME') = 'string'
                    and jsonb_typeof(text_colors_json -> 'WIN') = 'string'
                    and jsonb_typeof(text_colors_json -> 'TOTAL_KILLS') = 'string'
                    and jsonb_typeof(text_colors_json -> 'POSITION_POINTS') = 'string'
                    and jsonb_typeof(text_colors_json -> 'TOTAL_POINTS') = 'string'
                )
            )
        )
        or (
            labels_json ? 'matchesPlayed'
            and (
                text_colors_json is null
                or case
                    when jsonb_typeof(text_colors_json) = 'object'
                        and (text_colors_json - 'TEAM_NAME' - 'WIN' - 'TOTAL_KILLS' -
                            'POSITION_POINTS' - 'TOTAL_POINTS') = '{}'::jsonb
                        and jsonb_typeof(text_colors_json -> 'TEAM_NAME') = 'string'
                        and jsonb_typeof(text_colors_json -> 'WIN') = 'string'
                        and jsonb_typeof(text_colors_json -> 'TOTAL_KILLS') = 'string'
                        and jsonb_typeof(text_colors_json -> 'POSITION_POINTS') = 'string'
                        and jsonb_typeof(text_colors_json -> 'TOTAL_POINTS') = 'string'
                    then true
                    else false
                end
                or case
                    when jsonb_typeof(text_colors_json) = 'object'
                        and (text_colors_json - 'TEAM_NAME' - 'WIN' - 'MATCHES_PLAYED' -
                            'TOTAL_KILLS' - 'POSITION_POINTS' - 'TOTAL_POINTS') = '{}'::jsonb
                        and jsonb_typeof(text_colors_json -> 'TEAM_NAME') = 'string'
                        and jsonb_typeof(text_colors_json -> 'WIN') = 'string'
                        and jsonb_typeof(text_colors_json -> 'MATCHES_PLAYED') = 'string'
                        and jsonb_typeof(text_colors_json -> 'TOTAL_KILLS') = 'string'
                        and jsonb_typeof(text_colors_json -> 'POSITION_POINTS') = 'string'
                        and jsonb_typeof(text_colors_json -> 'TOTAL_POINTS') = 'string'
                    then true
                    else false
                end
            )
        )
    );
