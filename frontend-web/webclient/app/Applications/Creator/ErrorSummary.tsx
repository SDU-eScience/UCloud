// Error summary for the creator
// =====================================================================================================================
// The editor reports two kinds of errors to the user: parse errors (from the source text) and
// semantic validation errors (from the structured model). Both appear in a single list at the
// top of the main content area.
//
// Selecting a parse error switches to the YAML view and jumps to the line. Selecting a semantic
// error that names a parameter selects that parameter in the visual editor. Errors that do not
// name a parameter (global errors) just switch to the editor view.
//
// The summary is the bridge between the two error sources and the two views. It does not own the
// errors; it reads `parseErrors` and `validation.errors` from the draft.

import * as React from "react";
import {injectStyle} from "@/Unstyled";
import {Flex, Icon, Text} from "@/ui-components";
import {CreatorDraft} from "@/Applications/Creator/Draft";
import {CreatorSourceParseError} from "@/Applications/Creator/SourceParser";

export interface ErrorSummaryProps {
    // The current draft. The summary reads `parseErrors` and `validation.errors`.
    draft: CreatorDraft;
    // Called when the user clicks a parse error. The YAML editor scrolls to the line.
    onJumpToSourceLine: (line: number, column: number) => void;
    // Called when the user clicks a semantic error that names a parameter. The editor selects
    // that parameter. Called with null for global errors that do not name a parameter.
    onFocusParameter: (parameterName: string | null) => void;
}

export function ErrorSummary(props: ErrorSummaryProps): React.ReactNode {
    const {draft} = props;
    const parseErrors = draft.parseErrors ?? [];
    const validationErrors = draft.validation.errors;

    if (parseErrors.length === 0 && validationErrors.length === 0) return null;

    return (
        <div className={ErrorSummaryClass} id="creator-error-summary">
            <Flex alignItems="center" gap="8px" mb="8px">
                <Icon name="heroExclamationTriangle" size={16} color="errorMain" />
                <Text fontWeight={600} fontSize={14}>Errors</Text>
            </Flex>
            <ul className={ErrorListClass}>
                {parseErrors.map((e, i) => (
                    <ErrorSummaryItem
                        key={`p${i}`}
                        message={formatParseError(e)}
                        onClick={() => props.onJumpToSourceLine(e.line, e.column)}
                        kind="parse"
                    />
                ))}
                {validationErrors.map((e, i) => (
                    <ErrorSummaryItem
                        key={`v${i}`}
                        message={e.message}
                        onClick={() => props.onFocusParameter(e.parameterName)}
                        kind="validation"
                    />
                ))}
            </ul>
        </div>
    );
}

function ErrorSummaryItem(props: {message: string; onClick: () => void; kind: "parse" | "validation"}): React.ReactNode {
    return (
        <li className={ErrorItemClass} onClick={props.onClick} tabIndex={0}
            onKeyDown={e => {if (e.key === "Enter" || e.key === " ") {e.preventDefault(); props.onClick();}}}
        >
            <Icon
                name={props.kind === "parse" ? "heroCodeBracket" : "heroExclamationCircle"}
                size={14}
                color="errorMain"
            />
            <Text fontSize={13} className="error-item-message">{props.message}</Text>
        </li>
    );
}

function formatParseError(e: CreatorSourceParseError): string {
    if (e.line > 0) return `Line ${e.line}, column ${e.column}: ${e.message}`;
    return e.message;
}

const ErrorSummaryClass = injectStyle("creator-error-summary", k => `
    ${k} {
        max-width: 944px;
        background: var(--backgroundCard);
        box-shadow: var(--defaultShadow);
        border: 1px solid color-mix(in srgb, var(--errorMain) 40%, var(--borderColor));
        border-radius: 8px;
        padding: 12px 16px;
        box-sizing: border-box;
    }
`);

const ErrorListClass = injectStyle("creator-error-list", k => `
    ${k} {
        display: flex;
        flex-direction: column;
        gap: 4px;
        margin: 0;
        padding: 0;
        list-style: none;
    }
`);

const ErrorItemClass = injectStyle("creator-error-item", k => `
    ${k} {
        display: flex;
        align-items: flex-start;
        gap: 8px;
        padding: 4px 6px;
        border-radius: 4px;
        cursor: pointer;
        color: var(--textPrimary);
    }

    ${k}:hover, ${k}:focus {
        background: var(--backgroundCardHover);
        outline: none;
    }

    ${k} .error-item-message {
        min-width: 0;
        word-break: break-word;
    }
`);
