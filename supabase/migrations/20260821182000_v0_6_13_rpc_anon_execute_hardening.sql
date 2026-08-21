-- v0.6.13: remove anonymous EXECUTE access from protected RPCs.
--
-- These functions are intentionally callable by authenticated users, but
-- anonymous callers must not receive EXECUTE through either PUBLIC or an
-- explicit anon grant.

revoke execute on function public.finalize_match_snapshot(
    uuid,
    jsonb,
    jsonb,
    integer
) from public, anon;

revoke execute on function public.correct_finalized_match_snapshot(
    uuid,
    uuid,
    jsonb,
    integer,
    text
) from public, anon;

revoke execute on function public.claim_export_operation(
    text,
    uuid,
    uuid,
    text
) from public, anon;

revoke execute on function public.mark_export_operation_write_started(
    uuid,
    uuid
) from public, anon;

revoke execute on function public.complete_export_operation_success(
    uuid,
    uuid,
    integer,
    integer
) from public, anon;

revoke execute on function public.mark_export_operation_retryable_failure(
    uuid,
    uuid,
    text
) from public, anon;

revoke execute on function public.mark_export_operation_outcome_uncertain(
    uuid,
    uuid,
    text
) from public, anon;

revoke execute on function public.resolve_export_operation_verified_success(
    uuid,
    integer
) from public, anon;

grant execute on function public.finalize_match_snapshot(
    uuid,
    jsonb,
    jsonb,
    integer
) to authenticated;

grant execute on function public.correct_finalized_match_snapshot(
    uuid,
    uuid,
    jsonb,
    integer,
    text
) to authenticated;

grant execute on function public.claim_export_operation(
    text,
    uuid,
    uuid,
    text
) to authenticated;

grant execute on function public.mark_export_operation_write_started(
    uuid,
    uuid
) to authenticated;

grant execute on function public.complete_export_operation_success(
    uuid,
    uuid,
    integer,
    integer
) to authenticated;

grant execute on function public.mark_export_operation_retryable_failure(
    uuid,
    uuid,
    text
) to authenticated;

grant execute on function public.mark_export_operation_outcome_uncertain(
    uuid,
    uuid,
    text
) to authenticated;

grant execute on function public.resolve_export_operation_verified_success(
    uuid,
    integer
) to authenticated;
