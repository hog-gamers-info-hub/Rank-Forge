revoke all privileges on table public.match_ocr_evidence from authenticated;
revoke all privileges on table public.match_ocr_row_evidence from authenticated;
revoke all privileges on table public.match_ocr_correction_snapshots from authenticated;

grant select, insert, update, delete
on table public.match_ocr_evidence
to authenticated;
grant select, insert, update, delete
on table public.match_ocr_row_evidence
to authenticated;
grant select, insert, update, delete
on table public.match_ocr_correction_snapshots
to authenticated;
