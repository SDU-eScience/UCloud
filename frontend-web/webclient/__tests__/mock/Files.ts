import {SensitivityLevelMap} from "../../app/DefaultObjects";
import {File, FileType} from "../../app/Files";
import {AccessRight} from "../../app/Types";

export const mockFilesSensitivityConfidential: Page<File> = {
    itemsInTotal: 12,
    itemsPerPage: 10,
    pageNumber: 0,
    items: [
        {
            fileType: "FILE",
            path: "/home/user@user.telecity/Screenshot_2018-08-09 SDU-eScience SDUCloud-1.png",
            modifiedAt: 1535379900000,
            ownerName: "user@user.telecity",
            size: 3794,
            acl: [],
            sensitivityLevel: SensitivityLevelMap.CONFIDENTIAL,
            ownSensitivityLevel: SensitivityLevelMap.SENSITIVE,
            permissionAlert: null
        }, {
            fileType: "DIRECTORY",
            path: "/home/user@user.telecity/AABA",
            modifiedAt: 1534858191000,
            ownerName: "user@user.telecity",
            size: 1,
            acl: [{entity: {username: "user3@test.dk"}, rights: [AccessRight.WRITE]}],
            sensitivityLevel: SensitivityLevelMap.CONFIDENTIAL,
            ownSensitivityLevel: SensitivityLevelMap.SENSITIVE,
            permissionAlert: null
        }, {
            fileType: "DIRECTORY",
            path: "/home/user@user.telecity/Favorites",
            modifiedAt: 1534857733000,
            ownerName: "user@user.telecity",
            size: 6,
            acl: [],
            sensitivityLevel: SensitivityLevelMap.CONFIDENTIAL,
            ownSensitivityLevel: SensitivityLevelMap.CONFIDENTIAL,
            permissionAlert: null
        }, {
            fileType: "DIRECTORY",
            path: "/home/user@user.telecity/Jobs",
            modifiedAt: 1534836764000,
            ownerName: "user@user.telecity",
            size: 78,
            acl: [],
            sensitivityLevel: SensitivityLevelMap.CONFIDENTIAL,
            ownSensitivityLevel: SensitivityLevelMap.CONFIDENTIAL,
            permissionAlert: null
        }, {
            fileType: "DIRECTORY",
            path: "/home/user@user.telecity/A folder the sequel",
            modifiedAt: 1534836373000,
            ownerName: "user@user.telecity",
            size: 1,
            acl: [],
            sensitivityLevel: SensitivityLevelMap.CONFIDENTIAL,
            ownSensitivityLevel: SensitivityLevelMap.CONFIDENTIAL,
            permissionAlert: null
        }, {
            fileType: "DIRECTORY",
            path: "/home/user@user.telecity/B",
            modifiedAt: 1534244778000,
            ownerName: "user@user.telecity",
            size: 3,
            acl: [],
            sensitivityLevel: SensitivityLevelMap.CONFIDENTIAL,
            ownSensitivityLevel: SensitivityLevelMap.CONFIDENTIAL,
            permissionAlert: null
        }, {
            fileType: "DIRECTORY",
            path: "/home/user@user.telecity/rehjoerijhoerijh",
            modifiedAt: 1534170588000,
            ownerName: "user@user.telecity",
            size: 0,
            acl: [],
            sensitivityLevel: SensitivityLevelMap.CONFIDENTIAL,
            ownSensitivityLevel: SensitivityLevelMap.CONFIDENTIAL,
            permissionAlert: null
        }, {
            fileType: "DIRECTORY",
            path: "/home/user@user.telecity/Test",
            modifiedAt: 1533630132000,
            ownerName: "user@user.telecity",
            size: 0,
            acl: [],
            sensitivityLevel: SensitivityLevelMap.CONFIDENTIAL,
            ownSensitivityLevel: SensitivityLevelMap.CONFIDENTIAL,
            permissionAlert: null
        }, {
            fileType: "DIRECTORY",
            path: "/home/user@user.telecity/somefolder-60D6DAD4-DCF1-415E-B4D6-9BCEF028369E",
            modifiedAt: 1533555168000,
            ownerName: "user@user.telecity",
            size: 0,
            acl: [],
            sensitivityLevel: SensitivityLevelMap.CONFIDENTIAL,
            ownSensitivityLevel: SensitivityLevelMap.CONFIDENTIAL,
            permissionAlert: null
        }, {
            fileType: "DIRECTORY",
            path: "/home/user@user.telecity/somefolder-24ACCA0C-2FAA-4B6C-8F31-7F1E633A54DB",
            modifiedAt: 1533554955000,
            ownerName: "user@user.telecity",
            size: 0,
            acl: [],
            sensitivityLevel: SensitivityLevelMap.CONFIDENTIAL,
            ownSensitivityLevel: SensitivityLevelMap.CONFIDENTIAL,
            permissionAlert: null
        }],
    pagesInTotal: 2
};

export const newMockFile = ({
    type = "DIRECTORY",
    path,
    modifiedAt = new Date().getMilliseconds(),
    ownerName = "user3@user.dk",
    size = 128,
    acl = [],
    favorited = false,
    sensitivityLevel = SensitivityLevelMap.PRIVATE
}): File => ({
    fileType: type as FileType,
    path: `/home/${ownerName}/${path}`,
    modifiedAt,
    size,
    acl,
    ownSensitivityLevel: sensitivityLevel,
    ownerName,
    sensitivityLevel,
    permissionAlert: null
});

/* test("Error silencer", () =>
    expect(1).toBe(1)
); */
