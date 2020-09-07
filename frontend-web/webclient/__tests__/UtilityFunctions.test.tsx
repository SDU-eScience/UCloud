import {SensitivityLevel, SensitivityLevelMap} from "../app/DefaultObjects";
import {Acl, SortBy} from "../app/Files";
import {dateToString} from "../app/Utilities/DateUtilities";
import {getFilenameFromPath, sizeToString} from "../app/Utilities/FileUtilities";
import * as UF from "../app/UtilityFunctions";
import {mockFilesSensitivityConfidential, newMockFile} from "./mock/Files";
import {AccessRight} from "../app/Types";

// TO LOWER CASE AND CAPITALIZE

test("All upper case and numbers", () => {
    expect(UF.capitalized("ABACUS 2.0")).toBe("Abacus 2.0");
});

test("All lower case", () => {
    expect(UF.capitalized("abacus")).toBe("Abacus");
});

test("Mixed case and special characters", () => {
    expect(UF.capitalized("aBaCuS 2.0 !@#$%^&*()")).toBe("Abacus 2.0 !@#$%^&*()");
});

test("Empty string", () => {
    expect(UF.capitalized("")).toBe("");
});

// Add trailing slash

test("Add trailing slash to string", () =>
    expect(UF.addTrailingSlash("/home/test@user.dk")).toBe("/home/test@user.dk/")
);

test("Don't add trailing slash to string", () =>
    expect(UF.addTrailingSlash("/home/test@user.dk/")).toBe("/home/test@user.dk/")
);

test("Add trailing slash to empty string", () =>
    expect(UF.addTrailingSlash("")).toBe("")
);

// Remove trailing slash

test("Remove trailing slash from string", () =>
    expect(UF.removeTrailingSlash("/home/test@user.dk/")).toBe("/home/test@user.dk")
);

test("Don't remove trailing slash from string", () =>
    expect(UF.removeTrailingSlash("/home/test@user.dk")).toBe("/home/test@user.dk")
);

test("Empty string, no action", () =>
    expect(UF.removeTrailingSlash("")).toBe("")
);

// Prettier string

test("Prettify string", () => expect(UF.prettierString("HELLO,_WORLD")).toBe("Hello, world"));

test("Prettify string, upper and lower case", () => expect(UF.prettierString("hEllO,_WorlD")).toBe("Hello, world"));

test("Prettify lowercase string", () => expect(UF.prettierString("path")).toBe("Path"));

test("Prettify 'empty' string", () => expect(UF.prettierString("__")).toBe("  "));

// Blank or null

test("Blank string", () =>
    expect(UF.blankOrUndefined("          ")).toBe(true)
);

test("Characters surrounded by whitespace", () =>
    expect(UF.blankOrUndefined("   TEXT   ")).toBe(false)
);

// In range

test("In range", () =>
    expect(UF.inRange({status: 10, min: 0, max: 20})).toBe(true)
);

test("Out of range", () =>
    expect(UF.inRange({status: 0, min: 1, max: 10})).toBe(false)
);

test("On lowest part of range", () =>
    expect(UF.inRange({status: 0, min: 0, max: 10})).toBe(true)
);

test("On highest part of range", () =>
    expect(UF.inRange({status: 10, min: 0, max: 10})).toBe(true)
);

// In success range

test("In success range", () =>
    expect(UF.inSuccessRange(200)).toBe(true)
);

test("In success range, upper limit", () =>
    expect(UF.inSuccessRange(299)).toBe(true)
);

test("Outside success range", () =>
    expect(UF.inSuccessRange(199)).toBe(false)
);

// is5xxStatusCode

test("In 5xx range", () =>
    expect(UF.is5xxStatusCode(500)).toBe(true)
);

test("Outside 5xx range", () =>
    expect(UF.is5xxStatusCode(499)).toBe(false)
);

test("Upper 5xx range", () =>
    expect(UF.is5xxStatusCode(599)).toBe(true)
);

// Get owner from ACL

const mockAcls: Acl[] = [{
    entity: {username: "user3@test.dk"},
    rights: [
        AccessRight.READ
    ]
}];

test("Get multiple owners from Acls", () =>
    expect(UF.getMembersString(mockAcls)).toBe("2 members")
);

test("Get single owner from Acls", () =>
    expect(UF.getMembersString([])).toBe("Only You")
);

// Get extension from path

test("Get .exe extension from path", () =>
    expect(UF.extensionFromPath("/Home/user@user.dk/internetexplorer.exe")).toBe("exe")
);

test("Get .ico extension from path", () =>
    expect(UF.extensionFromPath("/Home/user@user.dk/internetexplorer.ico")).toBe("ico")
);

// Extension type

test("Code extension", () =>
    expect(UF.extensionType("ol")).toBe("code")
);

test("Image extension", () =>
    expect(UF.extensionType("png")).toBe("image")
);

test("Text extension", () =>
    expect(UF.extensionType("txt")).toBe("text")
);

test("Sound extension", () =>
    expect(UF.extensionType("wav")).toBe("audio")
);

test("Archive extension", () =>
    expect(UF.extensionType("gz")).toBe("archive")
);

test("PDF extension", () =>
    expect(UF.extensionType("pdf")).toBe("pdf")
);

test("Video extension", () =>
    expect(UF.extensionType("mpg")).toBe("video")
);

test("Binary extension", () =>
    expect(UF.extensionType("dat")).toBe("binary")
);

test("No extension", () =>
    expect(UF.extensionType(".exe")).toBeNull()
);

// Extension type from path

test("Extract code type from path", () =>
    expect(UF.extensionTypeFromPath("/Home/user@user.dk/README.md")).toBe("markdown")
);

test("Extract sound type from path", () =>
    expect(UF.extensionTypeFromPath("/Home/user@user.dk/startupsound.mp3")).toBe("audio")
);

test("Extract no type from path", () =>
    expect(UF.extensionTypeFromPath("/Home/user@user.dk/theme_hospital")).toBeNull()
);

describe("Icon from file path", () => {
    test("Dir", () => expect(UF.iconFromFilePath("home", "DIRECTORY").type).toBe("DIRECTORY"));
    test("File", () => expect(UF.iconFromFilePath("home", "FILE")).toStrictEqual({name: "home", type: "FILE"}));
    test("File with ext", () =>
        expect(UF.iconFromFilePath("home.txt", "FILE")).toStrictEqual({type: "FILE", name: "home.txt", ext: "txt"}));
    test("Jobs", () =>
        expect(UF.iconFromFilePath("/home/user@user.dk/Jobs", "DIRECTORY").type).toStrictEqual("RESULTFOLDER"));
    test("Favorites", () =>
        expect(UF.iconFromFilePath("/home/user/Favorites", "DIRECTORY").type).toStrictEqual("FAVFOLDER"));
    test("Shares", () =>
        expect(UF.iconFromFilePath("/home/user/Shares", "DIRECTORY").type).toStrictEqual("SHARESFOLDER"));
    test("Trash", () =>
        expect(UF.iconFromFilePath("/home/user/Trash", "DIRECTORY").type).toStrictEqual("TRASHFOLDER"));
});

test("To same UUID", () =>
    expect(UF.shortUUID("ABC")).toBe("ABC")
);

// Download allowed

test("Download allowed", () =>
    expect(UF.downloadAllowed(mockFilesSensitivityConfidential.items)).toBe(true)
);

const highSensitivityFile = newMockFile({
    type: "FILE",
    path: "SensitiveFile",
    modifiedAt: new Date().getMilliseconds(),
    acl: [],
    ownerName: "user@user3.dk",
    sensitivityLevel: SensitivityLevelMap.SENSITIVE,
    favorited: false,
    size: 128
});

test("Download disallowed", () =>
    expect(UF.downloadAllowed(mockFilesSensitivityConfidential.items.concat([highSensitivityFile]))).toBe(false)
);

describe("If Present", () => {
    test("Present", () => {
        const fun = jest.fn();
        UF.ifPresent(1, fun);
        expect(fun).toBeCalled();
    });

    test("Not present", () => {
        const fun = jest.fn();
        UF.ifPresent(undefined, fun);
        expect(fun).toBeCalledTimes(0);
    });
});

describe("defaultErrorHandler", () => {
    test("Todo", () =>
        expect(UF.defaultErrorHandler({request: new XMLHttpRequest(), response: undefined})).toBe(0)
    );
});

describe("Themes", () => {
    test("Stored", () => expect(UF.isLightThemeStored()).toBeTruthy());
    test("Setting theme", () => {
        expect(UF.isLightThemeStored()).toBeTruthy();
        UF.setSiteTheme(false);
        expect(UF.isLightThemeStored()).toBeFalsy();
        UF.setSiteTheme(true);
        expect(UF.isLightThemeStored()).toBeTruthy();
    });
});

describe("Sort by prettier string", () => {
    test("ACL", () => expect(UF.sortByToPrettierString(SortBy.ACL)).toBe("Members"));
    test("File Type", () => expect(UF.sortByToPrettierString(SortBy.FILE_TYPE)).toBe("File Type"));
    test("Modified at", () => expect(UF.sortByToPrettierString(SortBy.MODIFIED_AT)).toBe("Modified at"));
    test("Path", () => expect(UF.sortByToPrettierString(SortBy.PATH)).toBe("Filename"));
    test("Size", () => expect(UF.sortByToPrettierString(SortBy.SIZE)).toBe("Size"));
    test("Sensitivity", () => expect(UF.sortByToPrettierString(SortBy.SENSITIVITY_LEVEL)).toBe("File sensitivity"));
    test("default_example", () =>
        expect(UF.sortByToPrettierString("default_example" as SortBy)).toBe("Default example")
    );
});
