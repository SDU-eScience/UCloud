import * as XHRUtils from "Utilities/XHRUtils";

/* function unwrapError(e: any): ErrorMessage {
    const request: XMLHttpRequest = e.request;
    if (request === undefined) {
        return {statusCode: 500, errorMessage: "Internal Server Error"};
    }

    const defaultStatusText = HTTP_STATUS_CODES[request.status] ?
        HTTP_STATUS_CODES[request.status] : "Internal Server Error";

    if (!!e.response) return {statusCode: request.status, errorMessage: defaultStatusText};
    if (!!e.response.why) return {statusCode: request.status, errorMessage: e.response.why};

    return {statusCode: request.status, errorMessage: defaultStatusText};
} */

/* export async function unwrap<T = any>(
    httpResponse: Promise<{request: XMLHttpRequest, response: T}>
): Promise<T | ErrorMessage> {
    try {
        return (await httpResponse).response;
    } catch (e) {
        return unwrapError(e);
    }
} */

/* export function isError(obj): obj is ErrorMessage {
    return obj.statusCode !== undefined && obj.errorMessage !== undefined;
} */
// https://stackoverflow.com/a/30106551
// export function b64EncodeUnicode(str: string) {
// first we use encodeURIComponent to get percent-encoded UTF-8,
// then we convert the percent encodings into raw bytes which
// can be fed into btoa.
/*  return btoa(encodeURIComponent(str).replace(/%([0-9A-F]{2})/g,
     function toSolidBytes(match, p1) {
         return String.fromCharCode(parseInt("0x" + p1, 16));
     })
 );
}
*/

describe("isError", () => {
    test("false, empty", () => {
        expect(XHRUtils.isError({})).toBeFalsy();
    });

    test("false, no errorMessage", () => {
        expect(XHRUtils.isError({statusCode: 200})).toBeFalsy();
    });

    test("false, no statusCode", () => {
        expect(XHRUtils.isError({errorMessage: "200"})).toBeFalsy();
    });

    test("true", () => {
        expect(XHRUtils.isError({statusCode: 200, errorMessage: "Foo"})).toBeTruthy();
    });
});

describe("unwrap", () => {
    test("Success", async () => {
        const result = await XHRUtils.unwrap<string>(new Promise(resolve => resolve({request: {} as XMLHttpRequest, response: "success"})));
        expect(result).toBe("success");
    });

    describe("Failure", () => {
        test("No request", async () => {
            const result = await XHRUtils.unwrap(new Promise((_, reject) => reject({
                request: undefined,
                response: ""
            })));
            expect(result).toStrictEqual({statusCode: 500, errorMessage: "Internal Server Error"});
        });

        test("No response", async () => {
            const result = await XHRUtils.unwrap(new Promise((_, reject) => reject({
                request: {status: 507}, response: undefined
            })));
            expect(result).toStrictEqual({statusCode: 507, errorMessage: "Insufficient Storage"});
        });

        test("Empty response", async () => {
            const result = await XHRUtils.unwrap(new Promise((_, reject) => reject({
                request: {status: 507}, response: {}
            })));
            expect(result).toStrictEqual({statusCode: 507, errorMessage: "Insufficient Storage"});
        });

        test("With `why` response", async () => {
            const result = await XHRUtils.unwrap(new Promise((_, reject) => reject({
                request: {}, response: {why: "foobar"}
            })));
            expect(result).toStrictEqual({statusCode: undefined, errorMessage: "foobar"});
        });
    });
});

test("b64EncodeUnicode", () => {
    expect(XHRUtils.b64EncodeUnicode("This Is My String")).toBe("VGhpcyBJcyBNeSBTdHJpbmc=");
});
