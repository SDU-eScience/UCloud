const always = "always";
const off = "off";
const warn = "warn";
const error = "warn";

module.exports = {
    root: true,
    parser: "@typescript-eslint/parser",
    plugins: [
        "@typescript-eslint",
    ],
    parserOptions: {sourceTypes: "module"},
    extends: [
        "eslint:recommended",
        "plugin:react/recommended",
        "plugin:@typescript-eslint/eslint-recommended",
        "plugin:@typescript-eslint/recommended",
    ],
    rules: {
        "react/prop-types": off,
        "@typescript-eslint/ban-ts-ignore": off,
        "@typescript-eslint/adjacent-overload-signatures": error,
        "@typescript-eslint/array-type": off,
        "@typescript-eslint/ban-types": error,
        "@typescript-eslint/consistent-type-assertions": error,
        "@typescript-eslint/interface-name-prefix": off,
        "@typescript-eslint/no-empty-function": error,
        "@typescript-eslint/no-empty-interface": error,
        "@typescript-eslint/no-explicit-any": off,
        "@typescript-eslint/no-inferrable-types": off,
        "@typescript-eslint/no-misused-new": error,
        "@typescript-eslint/no-namespace": error,
        "@typescript-eslint/no-parameter-properties": off,
        "@typescript-eslint/no-use-before-define": off,
        "@typescript-eslint/no-var-requires": error,
        "@typescript-eslint/prefer-for-of": error,
        "@typescript-eslint/prefer-function-type": error,
        "@typescript-eslint/prefer-namespace-keyword": error,
        "@typescript-eslint/triple-slash-reference": error,
        "@typescript-eslint/unified-signatures": error,
        "@typescript-eslint/semi": warn,
        "@typescript-eslint/no-non-null-assertion": off,
        "@typescript-eslint/explicit-function-return-type": ["warn", {allowExpressions: true}],
        "eol-last": ["error", "always"],
        "arrow-parens": [
            off,
            "as-needed"
        ],
        "camelcase": error,
        "comma-dangle": "off",
        "complexity": "off",
        "constructor-super": error,
        "curly": "off",
        "dot-notation": error,
        "eqeqeq": [
            error,
            "smart"
        ],
        "guard-for-in": error,
        "id-blacklist": [
            error,
            "any",
            "Number",
            "number",
            "String",
            "string",
            "boolean",
            "Undefined"
        ],
        "id-match": error,
        "max-classes-per-file": "off",
        "max-len": [
            error,
            {
                "code": 120
            }
        ],
        "new-parens": error,
        "no-bitwise": "off",
        "no-caller": error,
        "no-case-declarations": off,
        "no-cond-assign": error,
        "no-console": [
            warn,
            {
                "allow": [
                    "dirxml",
                    "warn",
                    "error",
                    "dir",
                    "timeLog",
                    "assert",
                    "clear",
                    "count",
                    "countReset",
                    "group",
                    "groupCollapsed",
                    "groupEnd",
                    "table",
                    "Console",
                    "profile",
                    "profileEnd",
                    "timeStamp",
                    "context"
                ]
            }
        ],
        "no-debugger": error,
        "no-empty": error,
        "no-eval": "error",
        "no-fallthrough": "off",
        "no-invalid-this": "off",
        "no-multiple-empty-lines": "off",
        "no-new-wrappers": error,
        "no-shadow": [
            warn,
            {
                "hoist": "all"
            }
        ],
        "no-throw-literal": error,
        "no-trailing-spaces": error,
        "no-undef-init": error,
        "no-underscore-dangle": error,
        "no-unsafe-finally": error,
        "no-unused-expressions": error,
        "no-unused-labels": error,
        "no-var": error,
        "object-shorthand": error,
        "one-var": [
            error,
            "never"
        ],
        "prefer-const": error,
        "radix": error,
        "spaced-comment": error,
        "use-isnan": error,
        "valid-typeof": "off",
        // Currently doesn't work
        /* "@typescript-eslint/tslint/config": [
            error,
            {
                "rules": {
                    "jsdoc-format": true,
                    "jsx-alignment": true,
                    "jsx-curly-spacing": [
                        true,
                        "never"
                    ],
                    "jsx-equals-spacing": [
                        true,
                        "never"
                    ],
                    "jsx-key": true,
                    "jsx-no-bind": true,
                    "jsx-no-lambda": true,
                    "jsx-no-string-ref": true,
                    "jsx-self-close": true,
                    "jsx-space-before-trailing-slash": true,
                    "jsx-wrap-multiline": true,
                    "no-reference-import": true
                }
            }
        ] */
    },
    settings: {
        react: {
            version: "detect",  // Tells eslint-plugin-react to automatically detect the version of React to use
        },
    },
};