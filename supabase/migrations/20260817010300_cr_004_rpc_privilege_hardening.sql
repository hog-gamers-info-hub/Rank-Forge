-- CR-004: CREATE OR REPLACE preserves explicit routine grants.
-- Revoke anon directly so hosted privilege drift cannot survive the RPC replacement.

revoke all on function public.finalize_match_snapshot(uuid, jsonb, jsonb, integer) from public;
revoke execute on function public.finalize_match_snapshot(uuid, jsonb, jsonb, integer) from anon;
grant execute on function public.finalize_match_snapshot(uuid, jsonb, jsonb, integer) to authenticated;

revoke all on function public.correct_finalized_match_snapshot(uuid, uuid, jsonb, integer, text) from public;
revoke execute on function public.correct_finalized_match_snapshot(uuid, uuid, jsonb, integer, text) from anon;
grant execute on function public.correct_finalized_match_snapshot(uuid, uuid, jsonb, integer, text) to authenticated;
