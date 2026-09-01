// Language definition for invocation templates (bash + Jinja)
// =====================================================================================================================
// The invocation editor highlights bash with embedded Jinja tags. The creator owns this language
// (`bash-jinja`); the file editor keeps its separate `jinja2` language untouched.
//
// The tokenizer embeds a copy of Monaco's shell Monarch tokenizer (monaco-editor 0.56.0,
// esm/vs/languages/definitions/shell) and prepends Jinja tag rules. Gonja (the engine that renders
// invocations) uses `{{ }}`, `{% %}`, `{# #}` with optional whitespace control (`{{-`, `{%-`,
// `-}}`, `-%}`, `-#}`, `{%+`).
//
// The shell rules are an embedded copy because Monaco does not export them for composition. Keep
// in sync with monaco-editor when upgrading. The Jinja rules on top take precedence over the
// shell rules; a `{` that is not part of a Jinja delimiter still tokenizes as bash.

export const bashJinjaLanguageId = "bash-jinja";

// Shell keywords and builtins copied from Monaco's shell definition. Do not edit by hand; see the
// header comment about keeping this file in sync with monaco-editor.
const shellKeywords = [
    "if", "then", "do", "else", "elif", "while", "until", "for", "in", "esac", "fi", "fin", "fil",
    "done", "exit", "set", "unset", "export", "function",
];

const shellBuiltins = [
    "ab", "awk", "bash", "beep", "cat", "cc", "cd", "chown", "chmod", "chroot", "clear", "cp",
    "curl", "cut", "diff", "echo", "find", "gawk", "gcc", "get", "git", "grep", "hg", "kill",
    "killall", "ln", "ls", "make", "mkdir", "openssl", "mv", "nc", "node", "npm", "ping", "ps",
    "restart", "rm", "rmdir", "sed", "service", "sh", "shopt", "shred", "source", "sort", "sleep",
    "ssh", "start", "stop", "su", "sudo", "svn", "tee", "telnet", "top", "touch", "vi", "vim",
    "wall", "wc", "wget", "who", "write", "yes", "zsh",
];

// Jinja statement keywords inside `{% %}`. Gonja's safe control-structure set: autoescape,
// filter, for, if, macro, raw, set, with, plus end tags and if/elif/else.
const jinjaStatementKeywords = [
    "for", "endfor", "if", "elif", "else", "endif", "set", "macro", "endmacro", "filter",
    "endfilter", "autoescape", "endautoescape", "with", "endwith", "raw", "endraw",
];

// Expression keywords inside both `{{ }}` and `{% %}` tag bodies.
const jinjaExpressionKeywords = [
    "true", "True", "false", "False", "none", "None", "and", "or", "not", "in", "is", "recursive",
];

// Monarch definition for the bash-jinja language.
export const bashJinjaMonarchTokens = {
    defaultToken: "",
    ignoreCase: false,
    tokenPostfix: "",
    startingWithDash: /-+\w+/,
    identifiersWithDashes: /[a-zA-Z]\w+(?:@startingWithDash)+/,
    symbols: /[=><!~?&|+\-*\/\^;\.,]+/,

    keywords: shellKeywords,
    builtins: shellBuiltins,
    jinjaStatementKeywords,
    jinjaExpressionKeywords,

    tokenizer: {
        root: [
            // Jinja tags take precedence over all shell rules. Whitespace-control markers
            // ({{- {%- {%+ {#- -}} -%} -#}) are part of the delimiters.
            [/\{\{/, "delimiter.bracket.jinja2", "@jinjaVariable"],
            [/\{%[+-]?/, "delimiter.bracket.jinja2", "@jinjaStatement"],
            [/\{#[+-]?/, "comment.jinja2", "@jinjaComment"],

            // Shell tokenizer rules (vendored from monaco-editor shell).
            [/@identifiersWithDashes/, ""],
            [/(\s)((?:@startingWithDash)+)/, ["white", "attribute.name"]],
            [
                /[a-zA-Z]\w*/,
                {
                    cases: {
                        "@keywords": "keyword",
                        "@builtins": "type.identifier",
                        "@default": "",
                    },
                },
            ],
            {include: "@whitespace"},
            {include: "@strings"},
            {include: "@parameters"},
            {include: "@heredoc"},
            [/[{}\[\]()]/, "@brackets"],
            [/@symbols/, "delimiter"],
            {include: "@numbers"},
            [/[,;]/, "delimiter"],
        ],

        whitespace: [
            [/\s+/, "white"],
            [/(^#!.*$)/, "metatag"],
            [/(^#.*$)/, "comment"],
        ],

        numbers: [
            [/\d*\.\d+([eE][-+]?\d+)?/, "number.float"],
            [/0[xX][0-9a-fA-F_]*[0-9a-fA-F]/, "number.hex"],
            [/\d+/, "number"],
        ],

        strings: [
            [/'/, "string", "@stringBody"],
            [/"/, "string", "@dblStringBody"],
        ],

        stringBody: [
            [/'/, "string", "@popall"],
            [/./, "string"],
        ],

        dblStringBody: [
            [/"/, "string", "@popall"],
            [/./, "string"],
        ],

        heredoc: [
            [
                /(<<[-<]?)(\s*)(['"`]?)([\w\-]+)(['"`]?)/,
                [
                    "constants",
                    "white",
                    "string.heredoc.delimiter",
                    "string.heredoc",
                    "string.heredoc.delimiter",
                ],
            ],
        ],

        parameters: [
            [/\$\d+/, "variable.predefined"],
            [/\$\w+/, "variable"],
            [/\$[*@#?\-$!0_]/, "variable"],
            [/\$'/, "variable", "@parameterBodyQuote"],
            [/\$"/, "variable", "@parameterBodyDoubleQuote"],
            [/\$\(/, "variable", "@parameterBodyParen"],
            [/\$\{/, "variable", "@parameterBodyCurlyBrace"],
        ],

        parameterBodyQuote: [
            [/[^#:%*@\-!_']+/, "variable"],
            [/[#:%*@\-!_]/, "delimiter"],
            [/'/, "variable", "@pop"],
        ],

        parameterBodyDoubleQuote: [
            [/[^#:%*@\-!_"]+/, "variable"],
            [/[#:%*@\-!_]/, "delimiter"],
            [/["]/, "variable", "@pop"],
        ],

        parameterBodyParen: [
            [/[^#:%*@\-!_)]+/, "variable"],
            [/[#:%*@\-!_]/, "delimiter"],
            [/[)]/, "variable", "@pop"],
        ],

        parameterBodyCurlyBrace: [
            [/[^#:%*@\-!_}]+/, "variable"],
            [/[#:%*@\-!_]/, "delimiter"],
            [/[}]/, "variable", "@pop"],
        ],

        // Jinja variable tags: {{ expression }}
        jinjaVariable: [
            [/[-+]?}}/, "delimiter.bracket.jinja2", "@popall"],
            {include: "@jinjaExpression"},
        ],

        // Jinja statement tags: {% for ... %}. The leading word is the statement keyword when
        // known; everything after it is an expression. `{% raw %}` switches to a body state that
        // treats the content as plain bash until `{% endraw %}`.
        jinjaStatement: [
            [/[-+]?%\}/, "delimiter.bracket.jinja2", "@popall"],
            [/[a-zA-Z_][a-zA-Z0-9_]*/, {
                cases: {
                    "raw": {token: "keyword.jinja2", next: "@jinjaRawTagEnd"},
                    "@jinjaStatementKeywords": "keyword.jinja2",
                    "@default": {token: "variable.name.jinja2", next: "@jinjaExpressionTail"},
                },
            }],
            {include: "@jinjaExpression"},
        ],

        // Waits for the closing `%}` of `{% raw %}` then enters the raw body.
        jinjaRawTagEnd: [
            [/\s+/, "white"],
            [/[-+]?%\}/, {token: "delimiter.bracket.jinja2", next: "@jinjaRawBody"}],
        ],

        // Raw body: plain bash until the `{% endraw %}` opening delimiter.
        jinjaRawBody: [
            [/\{%[+-]?/, {token: "delimiter.bracket.jinja2", next: "@jinjaEndrawTag"}],
            [/[^{]+/, ""],
            [/\{/, ""],
        ],

        // The `{% endraw %}` tag itself.
        jinjaEndrawTag: [
            [/\s*endraw/, {
                cases: {
                    "@default": {token: "keyword.jinja2", next: "@jinjaTagEndOnly"},
                },
            }],
            [/[a-zA-Z_][a-zA-Z0-9_]*/, {token: "", next: "@jinjaRawBody"}],
        ],

        // Consumes the final `%}` of the endraw tag and returns to bash.
        jinjaTagEndOnly: [
            [/\s+/, "white"],
            [/[-+]?%\}/, "delimiter.bracket.jinja2", "@popall"],
        ],

        // Jinja comment tags: {# ... #}
        jinjaComment: [
            [/[-+]?#\}/, "comment.jinja2", "@popall"],
            [/[\s\S]/, "comment.jinja2"],
        ],

        // Shared expression rules inside Jinja tags. Strings match before the quote catch-alls so
        // complete strings win; a lone unterminated quote still consumes as a string token.
        jinjaExpression: [
            [/"([^"\\]|\\.)*"/, "string.jinja2"],
            [/'([^'\\]|\\.)*'/, "string.jinja2"],
            [/\d+\.\d+/, "number.float.jinja2"],
            [/\d+/, "number.jinja2"],
            [/[a-zA-Z_][a-zA-Z0-9_]*/, {
                cases: {
                    "@jinjaExpressionKeywords": "keyword.jinja2",
                    "@default": "variable.name.jinja2",
                },
            }],
            [/~|==|!=|<=|>=|\||\+|-|\*|\/|%|!|<|>|=|\(|\)|\[|\]|,|\./, "operator.jinja2"],
            [/["']/, "string.jinja2"],
            [/\s+/, "white"],
        ],

        // Expression tokens after the leading word of a statement tag.
        jinjaExpressionTail: [
            [/[-+]?%\}/, "delimiter.bracket.jinja2", "@popall"],
            {include: "@jinjaExpression"},
        ],
    },
};

// Language configuration for brackets/comments used by Monaco's auto-closing and matching.
export const bashJinjaLanguageConfiguration = {
    comments: {
        lineComment: "#",
    },
    brackets: [
        ["{", "}"],
        ["[", "]"],
        ["(", ")"],
    ],
    autoClosingPairs: [
        {open: "{", close: "}"},
        {open: "[", close: "]"},
        {open: "(", close: ")"},
        {open: "\"", close: "\""},
        {open: "'", close: "'"},
        {open: "`", close: "`"},
    ],
    surroundingPairs: [
        {open: "{", close: "}"},
        {open: "[", close: "]"},
        {open: "(", close: ")"},
        {open: "\"", close: "\""},
        {open: "'", close: "'"},
        {open: "`", close: "`"},
    ],
};
