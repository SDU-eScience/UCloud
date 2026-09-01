// Static catalog of Jinja symbols in scope for invocation templates
// =====================================================================================================================
// This catalog mirrors, as data, the symbols that exist at render time on K8s. The authoritative
// sources are:
//
// - Gonja builtins: provider-integration/gonja/builtins/{filters,tests,global_functions,methods}.go
// - UCloud extensions: provider-integration/im2/pkg/controller/jinja_invocation.go (flag/option
//   filters, match/search tests)
// - K8s context: provider-integration/im2/pkg/integrations/k8s/cli_script_gen.go (ternary,
//   dynamicInterface, setInterfaceName) and
//   provider-integration/im2/pkg/integrations/k8s/containers/invocation.go (the ucloud object)
//
// Keep this catalog in sync with those files. The descriptions come from InvocationHelp.md. When
// the backend adds a filter or function, add it here.
//
// Slurm-only symbols are deliberately absent because K8s is the only supported target at the
// moment. Slurm-only symbols are: the `sbatch` and `script` functions, and `ucloud.webPort`,
// `ucloud.vncPort`, `ucloud.partition`, `ucloud.qos`.

export type InvocationValueKind =
    | "string"       // string
    | "number"       // int or float
    | "boolean"      // bool
    | "list"         // list of unknown items
    | "dict"         // mapping with unknown keys
    | "function"     // callable
    | "namespace"    // namespace object created by namespace()
    | "cycler"       // cycler created by cycler()
    | "joiner"       // joiner created by joiner()
    | "macro"        // macro defined in the template
    | "unknown";     // type not statically known

export interface InvocationSymbolDoc {
    // The symbol name.
    name: string;
    // Signature shown in completion detail and hover, e.g. "option(optionFlag[, addSpace])".
    signature: string;
    // One-line description shown in completion documentation and hover.
    description: string;
}

// Functions callable anywhere in an expression, from the K8s context and gonja builtins.
export const invocationFunctions: InvocationSymbolDoc[] = [
    {name: "ternary", signature: "ternary(condition, ifTrue, ifFalse)", description: "Returns ifTrue or ifFalse based on condition."},
    {name: "dynamicInterface", signature: "dynamicInterface(rank, type, target, port)", description: "Registers a dynamic WEB or VNC interface for a process rank."},
    {name: "setInterfaceName", signature: "setInterfaceName(name)", description: "Sets the name of a dynamic interface."},
    {name: "dict", signature: "dict(**values)", description: "Creates a dictionary from keyword arguments."},
    {name: "namespace", signature: "namespace(**values)", description: "Creates a mutable namespace for values shared across scopes."},
    {name: "range", signature: "range([start,] stop[, step])", description: "Produces integer values from start up to, but not including, stop."},
    {name: "cycler", signature: "cycler(values...)", description: "Cycles through values. Use .next() and .reset()."},
    {name: "joiner", signature: "joiner(sep=',')", description: "Returns an empty string on the first call and sep on later calls."},
    {name: "lipsum", signature: "lipsum(n=5, html=true, min=20, max=100)", description: "Generates lorem ipsum text. Primarily useful for testing."},
];

// Filters applied with `|`. Includes the UCloud `flag` and `option` filters plus gonja builtins.
export const invocationFilters: InvocationSymbolDoc[] = [
    {name: "flag", signature: "flag(onFlag[, offFlag])", description: "Returns onFlag for a true value and offFlag otherwise. The default offFlag is empty."},
    {name: "option", signature: "option(optionFlag[, addSpace])", description: "Adds a shell-escaped option and value when the input is not None. Spacing is inferred from whether optionFlag ends in = unless addSpace is supplied."},
    {name: "abs", signature: "abs", description: "Absolute value."},
    {name: "attr", signature: "attr(name)", description: "Read an attribute without item lookup."},
    {name: "batch", signature: "batch(size)", description: "Group values into fixed-size lists."},
    {name: "capitalize", signature: "capitalize", description: "Uppercase the first character and lowercase the rest."},
    {name: "center", signature: "center(width)", description: "Center text to a given width."},
    {name: "default", signature: "default(value[, boolean])", description: "Use a fallback for an undefined value."},
    {name: "d", signature: "d", description: "Alias for default."},
    {name: "dictsort", signature: "dictsort", description: "Sort dictionary items."},
    {name: "escape", signature: "escape", description: "Escape HTML characters."},
    {name: "e", signature: "e", description: "Alias for escape."},
    {name: "filesizeformat", signature: "filesizeformat", description: "Format a number as a human-readable file size."},
    {name: "first", signature: "first", description: "Return the first item."},
    {name: "float", signature: "float", description: "Convert to a floating-point number."},
    {name: "forceescape", signature: "forceescape", description: "Force HTML escaping."},
    {name: "format", signature: "format(...)", description: "Apply printf-style formatting."},
    {name: "groupby", signature: "groupby(attribute)", description: "Group values by an attribute."},
    {name: "indent", signature: "indent(width=4)", description: "Indent each line of text."},
    {name: "int", signature: "int", description: "Convert to an integer."},
    {name: "join", signature: "join(d='')", description: "Join a sequence into a string."},
    {name: "last", signature: "last", description: "Return the last item."},
    {name: "length", signature: "length", description: "Return the number of items."},
    {name: "list", signature: "list", description: "Convert a value to a list."},
    {name: "lower", signature: "lower", description: "Convert to lowercase."},
    {name: "map", signature: "map(...)", description: "Map an attribute or filter over a sequence."},
    {name: "max", signature: "max", description: "Return the largest item."},
    {name: "min", signature: "min", description: "Return the smallest item."},
    {name: "pprint", signature: "pprint", description: "Pretty-print a value."},
    {name: "random", signature: "random", description: "Return a random item."},
    {name: "rejectattr", signature: "rejectattr(attribute)", description: "Reject values whose attribute passes a test."},
    {name: "reject", signature: "reject(test)", description: "Reject values that pass a test."},
    {name: "replace", signature: "replace(old, new)", description: "Replace occurrences of a substring."},
    {name: "reverse", signature: "reverse", description: "Reverse a value or iterator."},
    {name: "round", signature: "round(precision=0)", description: "Round a number."},
    {name: "safe", signature: "safe", description: "Mark a value as safe from automatic escaping."},
    {name: "selectattr", signature: "selectattr(attribute)", description: "Select values whose attribute passes a test."},
    {name: "select", signature: "select(test)", description: "Select values that pass a test."},
    {name: "slice", signature: "slice(slices)", description: "Split an iterator into lists."},
    {name: "sort", signature: "sort", description: "Sort an iterable."},
    {name: "string", signature: "string", description: "Convert a value to a string."},
    {name: "striptags", signature: "striptags", description: "Remove HTML/XML tags."},
    {name: "sum", signature: "sum", description: "Sum values, optionally by an attribute."},
    {name: "title", signature: "title", description: "Convert text to title case."},
    {name: "tojson", signature: "tojson(indent)", description: "Serialize a value as JSON."},
    {name: "trim", signature: "trim(chars=' ')", description: "Remove leading and trailing whitespace."},
    {name: "truncate", signature: "truncate(length=255)", description: "Limit text to a given length."},
    {name: "unique", signature: "unique", description: "Remove duplicate values."},
    {name: "upper", signature: "upper", description: "Convert to uppercase."},
    {name: "urlencode", signature: "urlencode", description: "Encode a value for a URL."},
    {name: "urlize", signature: "urlize", description: "Convert URLs in text to links."},
    {name: "wordcount", signature: "wordcount", description: "Count words in text."},
    {name: "wordwrap", signature: "wordwrap(width)", description: "Wrap text to a given width."},
    {name: "xmlattr", signature: "xmlattr", description: "Create XML/HTML attributes from a dictionary."},
];

// Tests usable with `is`. Includes the UCloud `match` and `search` tests plus gonja builtins.
export const invocationTests: InvocationSymbolDoc[] = [
    {name: "defined", signature: "defined", description: "Whether a value exists."},
    {name: "undefined", signature: "undefined", description: "Whether a value does not exist."},
    {name: "none", signature: "none", description: "Whether a value is None."},
    {name: "callable", signature: "callable", description: "Whether the value is callable."},
    {name: "iterable", signature: "iterable", description: "Checks whether the value can be iterated."},
    {name: "mapping", signature: "mapping", description: "Whether the value is a mapping."},
    {name: "sequence", signature: "sequence", description: "The value is a sequence."},
    {name: "string", signature: "string", description: "Whether the value is a string."},
    {name: "number", signature: "number", description: "Whether the value is a number."},
    {name: "even", signature: "even", description: "Whether an integer is even."},
    {name: "odd", signature: "odd", description: "Whether an integer is odd."},
    {name: "divisibleby", signature: "divisibleby(num)", description: "Whether an integer is divisible by num."},
    {name: "eq", signature: "eq(value)", description: "Whether the value equals the argument."},
    {name: "equalto", signature: "equalto(value)", description: "Alias for eq."},
    {name: "ne", signature: "ne(value)", description: "Whether the value does not equal the argument."},
    {name: "gt", signature: "gt(value)", description: "Whether the value is greater than the argument."},
    {name: "greaterthan", signature: "greaterthan(value)", description: "Alias for gt."},
    {name: "ge", signature: "ge(value)", description: "The value is greater than or equal to the argument."},
    {name: "lt", signature: "lt(value)", description: "Whether the value is less than the argument."},
    {name: "lessthan", signature: "lessthan(value)", description: "Alias for lt."},
    {name: "le", signature: "le(value)", description: "The value is less than or equal to the argument."},
    {name: "in", signature: "in(seq)", description: "Whether the value occurs in a sequence or mapping."},
    {name: "lower", signature: "lower", description: "Whether a string is all lowercase."},
    {name: "upper", signature: "upper", description: "Whether a string is all uppercase."},
    {name: "sameas", signature: "sameas(other)", description: "Checks object identity."},
    {name: "match", signature: "match(pattern[, ignorecase][, multiline])", description: "Matches a regular expression against the complete string."},
    {name: "search", signature: "search(pattern[, ignorecase][, multiline])", description: "Searches for a regular expression in a string."},
];

// Attribute and method names per value kind, from gonja's builtins/methods.
export interface InvocationMembers {
    // Names readable with `.name` syntax.
    attributes: string[];
    // Methods callable with `.name(...)`.
    methods: InvocationSymbolDoc[];
}

function methodDocs(names: string[], receiver: string): InvocationSymbolDoc[] {
    return names.map(m => ({name: m, signature: `${receiver}.${m}(...)`, description: `${receiver} method.`}));
}

export const invocationMembers: Record<InvocationValueKind, InvocationMembers> = {
    string: {
        attributes: [],
        methods: methodDocs([
            "capitalize", "capwords", "casefold", "center", "count", "encode", "endswith",
            "expandtabs", "find", "format", "format_map", "isalnum", "isalpha", "isascii",
            "isdecimal", "isdigit", "islower", "isnumeric", "isprintable", "isspace", "istitle",
            "isupper", "join", "ljust", "lower", "lstrip", "partition", "removeprefix",
            "removesuffix", "replace", "rfind", "rjust", "rpartition", "rsplit", "rstrip", "split",
            "splitlines", "startswith", "strip", "swapcase", "title", "upper", "zfill",
        ], "string"),
    },
    number: {
        attributes: [],
        methods: methodDocs([
            "is_integer", "bit_length", "bit_count", "as_integer_ratio",
        ], "number"),
    },
    boolean: {
        attributes: [],
        methods: methodDocs([
            "string", "int", "bit_length", "bit_count", "as_integer_ratio",
        ], "bool"),
    },
    list: {
        attributes: [],
        methods: methodDocs(["append", "copy", "reverse"], "list"),
    },
    dict: {
        attributes: [],
        methods: methodDocs(["keys"], "dict"),
    },
    function: {attributes: [], methods: []},
    namespace: {attributes: [], methods: []},
    cycler: {
        attributes: ["current"],
        methods: [
            {name: "next", signature: "cycler.next()", description: "Advances the cycler and returns the previous current value."},
            {name: "reset", signature: "cycler.reset()", description: "Resets the cycler to the first value."},
        ],
    },
    joiner: {attributes: [], methods: []},
    macro: {attributes: [], methods: []},
    unknown: {attributes: [], methods: []},
};

// The `ucloud` object tree from the K8s container renderer (containers/invocation.go). Each leaf
// carries its value kind so member completion works below `ucloud.machine.` etc.
export interface InvocationUcloudMember {
    name: string;
    kind: InvocationValueKind;
    description: string;
    // Present when this member is itself an object with members.
    children?: InvocationUcloudMember[];
}

export const ucloudObject: InvocationUcloudMember[] = [
    {name: "jobId", kind: "string", description: "The stable UCloud job ID."},
    {
        name: "machine",
        kind: "dict",
        description: "The allocated machine.",
        children: [
            {name: "name", kind: "string", description: "The allocated machine slice name."},
            {name: "category", kind: "string", description: "The machine category name."},
            {name: "cpu", kind: "number", description: "Number of allocated CPUs."},
            {name: "cpuModel", kind: "string", description: "CPU model, when configured."},
            {name: "memoryInGigs", kind: "number", description: "Allocated memory in gigabytes."},
            {name: "memoryModel", kind: "string", description: "Memory model, when configured."},
            {name: "gpu", kind: "number", description: "Number of allocated GPUs."},
            {name: "gpuModel", kind: "string", description: "GPU model, when configured."},
        ],
    },
    {name: "nodes", kind: "number", description: "Number of allocated nodes."},
    {name: "rank", kind: "number", description: "Zero-based rank of the current process."},
    {
        name: "application",
        kind: "dict",
        description: "The application being submitted.",
        children: [
            {name: "name", kind: "string", description: "Name of the application being submitted."},
            {name: "version", kind: "string", description: "Version of the application being submitted."},
        ],
    },
];

// Members of `loop` inside a for-loop, from gonja's LoopInfos (for.go). The doc lists index,
// index0, revindex, revindex0, first, last, length plus cycle/changed.
export const loopMembers: InvocationMembers = {
    attributes: ["index", "index0", "revindex", "revindex0", "first", "last", "length"],
    methods: [
        {name: "cycle", signature: "loop.cycle(...)", description: "Returns one of the arguments by loop position."},
        {name: "changed", signature: "loop.changed(value)", description: "True when the value differs from the previous iteration."},
    ],
};

// Statement tags accepted by gonja's safe control structure set.
export const invocationStatementTags: InvocationSymbolDoc[] = [
    {name: "for", signature: "for item in seq", description: "Loop over a sequence."},
    {name: "if", signature: "if cond", description: "Conditional block."},
    {name: "set", signature: "set name = value", description: "Assign a variable."},
    {name: "macro", signature: "macro name(args)", description: "Define a macro."},
    {name: "filter", signature: "filter name", description: "Apply a filter to a block."},
    {name: "with", signature: "with x = v", description: "Scope variables to a block."},
    {name: "autoescape", signature: "autoescape", description: "Toggle autoescaping for a block."},
    {name: "raw", signature: "raw", description: "Emit the block without Jinja processing."},
    {name: "elif", signature: "elif cond", description: "Else-if branch."},
    {name: "else", signature: "else", description: "Else branch."},
    {name: "endfor", signature: "endfor", description: "End of for block."},
    {name: "endif", signature: "endif", description: "The end of an if block."},
    {name: "endmacro", signature: "endmacro", description: "End of macro block."},
    {name: "endfilter", signature: "endfilter", description: "End of filter block."},
    {name: "endwith", signature: "endwith", description: "End of with block."},
    {name: "endautoescape", signature: "endautoescape", description: "End of autoescape block."},
    {name: "endraw", signature: "endraw", description: "End of raw block."},
];

// Tags that gonja supports but UCloud disallows in invocations (file/template loading).
export const unsupportedStatementTags = ["include", "import", "from", "extends", "block"];

// Statement tags that must be closed by a matching end tag.
export const statementEndPairs: Record<string, string> = {
    "for": "endfor",
    "if": "endif",
    "macro": "endmacro",
    "filter": "endfilter",
    "with": "endwith",
    "autoescape": "endautoescape",
    "raw": "endraw",
};
