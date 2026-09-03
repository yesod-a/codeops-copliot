INSERT INTO review_policies (
    project_id,
    enabled,
    pre_commit_enabled,
    pre_push_enabled,
    post_merge_enabled,
    fail_on_severity,
    fail_open,
    updated_at
)
SELECT
    p.id,
    TRUE,
    FALSE,
    TRUE,
    FALSE,
    'HIGH',
    FALSE,
    CURRENT_TIMESTAMP(6)
FROM projects p
LEFT JOIN review_policies rp ON rp.project_id = p.id
WHERE rp.id IS NULL;
