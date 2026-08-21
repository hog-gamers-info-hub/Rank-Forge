alter table public.match_results
    alter constraint match_results_team_slot_id_fkey
    deferrable initially deferred;

alter table public.match_correction_audit_entries
    alter constraint match_correction_audit_entries_match_result_id_fkey
    deferrable initially deferred;

alter table public.match_correction_audit_entries
    alter constraint match_correction_audit_entries_team_slot_id_fkey
    deferrable initially deferred;
