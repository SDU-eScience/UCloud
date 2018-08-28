import * as UF from "UtilityFunctions";
import { SortBy, SortOrder, Acl } from "Files";

// TO LOWER CASE AND CAPITALIZE

test("All upper case and numbers", () => {
    expect(UF.toLowerCaseAndCapitalize("ABACUS 2.0")).toBe("Abacus 2.0");
});

test("All lower case", () => {
    expect(UF.toLowerCaseAndCapitalize("abacus")).toBe("Abacus");
});

test("Mixed case and special characters", () => {
    expect(UF.toLowerCaseAndCapitalize("aBaCuS 2.0 !@#$%^&*()")).toBe("Abacus 2.0 !@#$%^&*()")
});

test("Empty string", () => {
    expect(UF.toLowerCaseAndCapitalize("")).toBe("");
});

// Add trailing slash

test("Add trailing slash to string", () =>
    expect(UF.addTrailingSlash("/home/test@user.dk")).toBe("/home/test@user.dk/")
);

test("Don't add trailing slash to string", () =>
    expect(UF.addTrailingSlash("/home/test@user.dk/")).toBe("/home/test@user.dk/")
);

test("Add trailing slash to empty string", () =>
    expect(UF.addTrailingSlash("")).toBe("/")
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

test("Prettify string", () =>
    expect(UF.prettierString("HELLO,_WORLD")).toBe("Hello, world")
);

test("Prettify string, upper and lower case", () =>
    expect(UF.prettierString("hEllO,_WorlD")).toBe("Hello, world")
);

test("Prettify lowercase string", () =>
    expect(UF.prettierString("path")).toBe("Path")
);

test("Prettify 'empty' string", () =>
    expect(UF.prettierString("__")).toBe("  ")
);

// Blank or null

test("Blank string", () =>
    expect(UF.blankOrUndefined("          ")).toBe(true)
);

test("Characters surrounded by whitespace", () =>
    expect(UF.blankOrUndefined("   TEXT   ")).toBe(false)
);

// In range

test("In range", () =>
    expect(UF.inRange({ status: 10, min: 0, max: 20 })).toBe(true)
);

test("Out of range", () =>
    expect(UF.inRange({ status: 0, min: 1, max: 10 })).toBe(false)
);

test("On lowest part of range", () =>
    expect(UF.inRange({ status: 0, min: 0, max: 10 })).toBe(true)
);

test("On highest part of range", () =>
    expect(UF.inRange({ status: 10, min: 0, max: 10 })).toBe(true)
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

// Get sorting icon

test("Chevron up", () =>
    expect(UF.getSortingIcon(SortBy.PATH, SortOrder.ASCENDING, SortBy.PATH)).toBe("chevron up")
);

test("Chevron down", () =>
    expect(UF.getSortingIcon(SortBy.PATH, SortOrder.DESCENDING, SortBy.PATH)).toBe("chevron down")
);

test("Undefined", () =>
    expect(UF.getSortingIcon(SortBy.PATH, SortOrder.ASCENDING, SortBy.MODIFIED_AT)).toBeUndefined()
);

// Get owner from ACL

const mockAcls: Acl[] = [
    {
        entity: "user3@test.dk",
        rights: [
            "READ"
        ],
        group: false
    }
]

test("Get multiple owners from Acls", () =>
    expect(UF.getOwnerFromAcls(mockAcls)).toBe("2 members")
);

test("Get multiple owners from Acls", () =>
    expect(UF.getOwnerFromAcls([])).toBe("Only You")
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
    expect(UF.extensionType("wav")).toBe("sound")
);

test("Archive extension", () =>
    expect(UF.extensionType("gz")).toBe("archive")
);

test("No extension", () =>
    expect(UF.extensionType(".exe")).toBe("")
);

// Extension type from path

test("Extract code type from path", () =>
    expect(UF.extensionTypeFromPath("/Home/user@user.dk/README.md")).toBe("code")
);

test("Extract sound type from path", () =>
    expect(UF.extensionTypeFromPath("/Home/user@user.dk/startupsound.mp3")).toBe("sound")
);

test("Extract no type from path", () =>
    expect(UF.extensionTypeFromPath("/Home/user@user.dk/theme_hospital")).toBe("")
);

// Icon from file path

const HOME_FOLDER = "/home/user@test.dk/";

test("Code icon", () =>
    expect(UF.iconFromFilePath(`${HOME_FOLDER}code.cc`, "FILE", HOME_FOLDER)).toBe("file code outline")
);

test("Image icon", () =>
    expect(UF.iconFromFilePath(`${HOME_FOLDER}profile_iamge.png`, "FILE", HOME_FOLDER)).toBe("image")
);

test("Text icon", () =>
    expect(UF.iconFromFilePath(`${HOME_FOLDER}notes.txt`, "FILE", HOME_FOLDER)).toBe("file outline")
);

test("Sound icon", () =>
    expect(UF.iconFromFilePath(`${HOME_FOLDER}theme_song.wav`, "FILE", HOME_FOLDER)).toBe("volume up")
);

test("Archive icon", () =>
    expect(UF.iconFromFilePath(`${HOME_FOLDER}files.zip`, "FILE", HOME_FOLDER)).toBe("file archive outline")
);

test("Directory icon", () =>
    expect(UF.iconFromFilePath(`${HOME_FOLDER}Content`, "DIRECTORY", HOME_FOLDER)).toBe("folder")
);

test("Jobs icon", () =>
    expect(UF.iconFromFilePath(`${HOME_FOLDER}Jobs/`, "DIRECTORY", HOME_FOLDER)).toBe("tasks")
);

test("Favorites icon", () =>
    expect(UF.iconFromFilePath(`${HOME_FOLDER}Favorites/`, "DIRECTORY", HOME_FOLDER)).toBe("star")
);

test("File outline icon, fallback", () =>
    expect(UF.iconFromFilePath(`${HOME_FOLDER}pacman.savegame`, "FILE", HOME_FOLDER)).toBe("file outline")
);

// Short UUID

test("To shortened UUID", () =>
    expect(UF.shortUUID("abcd-abcd-abcd")).toBe("ABCD-ABC")
);

test("To same UUDI", () =>
    expect(UF.shortUUID("ABC")).toBe("ABC")
);

// Download allowed

import { mockFiles_SensitivityConfidential, newMockFile } from "./mock/Files"

test("Download allowed", () =>
    expect(UF.downloadAllowed(mockFiles_SensitivityConfidential.items)).toBe(true)
);

const highSensitivityFile = newMockFile({
    type: "FILE",
    path: "SensitiveFile",
    createdAt: new Date().getMilliseconds() - 3600 * 24,
    modifiedAt: new Date().getMilliseconds(),
    acl: [],
    annotations: [],
    ownerName: "user@user3.dk",
    sensitivityLevel: "SENSITIVE",
    favorited: false,
    link: false,
    size: 128
});

test("Download disallowed", () =>
    expect(UF.downloadAllowed(mockFiles_SensitivityConfidential.items.concat([highSensitivityFile]))).toBe(false)
);