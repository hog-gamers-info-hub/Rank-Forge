alter table public.custom_design_templates
    add column average_ranking_bounding_box_height_px double precision;

alter table public.custom_design_templates
    add constraint custom_design_templates_average_ranking_bounding_box_height_check
    check (
        average_ranking_bounding_box_height_px is null
        or (
            average_ranking_bounding_box_height_px > 0
            and average_ranking_bounding_box_height_px <= source_height
        )
    );
