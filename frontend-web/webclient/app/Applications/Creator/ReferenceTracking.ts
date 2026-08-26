// Invocation reference tracking
// =====================================================================================================================
// On rename, the editor updates exact static references to the old parameter name in the Jinja
// invocation template. It does not rewrite dynamic expressions. On delete, the references remain
// unchanged. The validator reports unresolved references as blocking errors because there is no
// replacement value.
//
// The rewrite covers the `invocation` field only. A static reference is a Jinja variable tag that
// contains exactly the old name, with optional whitespace inside the braces. Examples:
//
//     {{ oldName }}     → rewritten
//     {{oldName}}       → rewritten
//     {{ oldName | upper }}  → NOT rewritten (dynamic expression)
//     {{ a.oldName }}   → NOT rewritten (member access)
//
// The rename uses a regex that matches the bare variable name only.

// Rewrite exact static references to `oldName` as `newName` in the invocation text. Returns the
// updated text. Dynamic expressions are left untouched.
export function rewriteInvocationReferences(invocation: string, oldName: string, newName: string): string {
    if (!oldName) return invocation;
    // Match {{ optional-ws word-boundary oldName word-boundary optional-ws }}.
    // The word boundaries prevent partial matches and member access (a.oldName). We allow internal
    // whitespace as Jinja does. We reject names followed by ` |` or `.` to avoid dynamic expressions.
    const escaped = escapeRegExp(oldName);
    const pattern = new RegExp(
        `\\{\\{\\s*\\b` + escaped + `\\b\\s*(?=[|}.]|\\s*\\}\\})\\s*\\}\\}`,
        "g"
    );
    return invocation.replace(pattern, `{{ ${newName} }}`);
}

function escapeRegExp(s: string): string {
    return s.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}
