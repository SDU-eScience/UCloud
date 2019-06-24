import { Dictionary } from "Types";

export const HTTP_STATUS_CODES: Dictionary<string> = {
    "100": "Continue",
    "101": "Switching Protocols",
    "102": "Processing",
    "200": "OK",
    "201": "Created",
    "202": "Accepted",
    "203": "Non-Authoritative Information",
    "204": "No Content",
    "205": "Reset Content",
    "206": "Partial Content",
    "207": "Multi-Status",
    "300": "Multiple Choices",
    "301": "Moved Permanently",
    "302": "Found",
    "303": "See Other",
    "304": "Not Modified",
    "305": "Use Proxy",
    "306": "Switch Proxy",
    "307": "Temporary Redirect",
    "308": "Permanent Redirect",
    "400": "Bad Request",
    "401": "Unauthorized",
    "402": "Payment Required",
    "403": "Forbidden",
    "404": "Not Found",
    "405": "Method Not Allowed",
    "406": "Not Acceptable",
    "407": "Proxy Authentication Required",
    "408": "Request Timeout",
    "409": "Conflict",
    "410": "Gone",
    "411": "Length Required",
    "412": "Precondition Failed",
    "413": "Payload Too Large",
    "414": "Request-URI Too Long",
    "415": "Unsupported Media Type",
    "416": "Requested Range Not Satisfiable",
    "417": "Expectation Failed",
    "422": "Unprocessable Entity",
    "423": "Locked",
    "424": "Failed Dependency",
    "426": "Upgrade Required",
    "429": "Too Many Requests",
    "431": "Request Header Fields Too Large",
    "500": "Internal Server Error",
    "501": "Not Implemented",
    "502": "Bad Gateway",
    "503": "Service Unavailable",
    "504": "Gateway Timeout",
    "505": "HTTP Version Not Supported",
    "506": "Variant Also Negotiates",
    "507": "Insufficient Storage",
};

export interface ErrorMessage {
    statusCode: number
    errorMessage: string
}

function unwrapError(e: any): ErrorMessage {
    const request: XMLHttpRequest = e.request;
    if (request === undefined) {
        return { statusCode: 500, errorMessage: "Internal Server Error" };
    }

    const defaultStatusText = HTTP_STATUS_CODES[request.status] ?
        HTTP_STATUS_CODES[request.status] : "Internal Server Error";

    if (!!e.response) return { statusCode: request.status, errorMessage: defaultStatusText };
    if (!!e.response.why) return { statusCode: request.status, errorMessage: e.response.why };

    return { statusCode: request.status, errorMessage: defaultStatusText };
}

export async function unwrap<T = any>(httpResponse: Promise<{ request: XMLHttpRequest, response: T }>): Promise<T | ErrorMessage> {
    try {
        return (await httpResponse).response;
    } catch (e) {
        return unwrapError(e);
    }
}

export function isError(obj: any): obj is ErrorMessage  {
    return obj.statusCode !== undefined && obj.errorMessage !== undefined;
}