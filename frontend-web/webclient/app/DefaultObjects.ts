import { tusConfig } from "./Configurations";
import * as Uppy from "uppy";
import { File, Analysis, Application, Status, Publication, SidebarOption, DropdownOption } from "./types/types"
import SDUCloud from "../authentication/lib";

export const DefaultStatus: Status = {
    title: "No Issues",
    level: "NO ISSUES",
    body: "The system is running as intended."
};

export enum KeyCode {
    ENTER = 13,
    ESC = 27,
    UP = 38,
    DOWN = 40,
    LEFT = 37,
    RIGHT = 39,
    A = 65
}

export const RightsMap: { [s: string]: number } = {
    "NONE": 0,
    "READ": 1,
    "READ_WRITE": 2,
    "EXECUTE": 3
};

export enum AnalysesStatusMap {
    "PENDING",
    "IN PROGRESS",
    "COMPLETED"
};

export const RightsNameMap: { [s: string]: string } = {
    "NONE": "None",
    "READ": "Read",
    "READ_WRITE": "Read/Write",
    "EXECUTE": "Execute"
};

export enum SensitivityLevel {
    "OPEN_ACCESS" = "Open Access",
    "CONFIDENTIAL" = "Confidential",
    "SENSITIVE" = "Sensitive"
};

export const SensitivityLevelMap: { [s: string]: number } = {
    "OPEN_ACCESS": 0,
    "CONFIDENTIAL": 1,
    "SENSITIVE": 2
};

interface UppyRestriction {
    maxFileSize?: false | number
    maxNumberOfFiles?: false | number
    minNumberOfFiles?: false | number
    allowedFileTypes: false | number
}

const initializeUppy = (restrictions: UppyRestriction, cloud: SDUCloud) =>
    Uppy.Core({
        autoProceed: false,
        debug: false,
        restrictions: restrictions,
        meta: {
            sensitive: false,
        },
        onBeforeUpload: () => {
            return cloud.receiveAccessTokenOrRefreshIt().then((data: string) => {
                tusConfig.headers["Authorization"] = `Bearer ${data}`;
            });
        }
    }).use(Uppy.Tus, tusConfig);


const getFilesSortingColumnOrDefault = (index: number): string => {
    const sortingColumn = window.localStorage.getItem(`filesSorting${index}`);
    if (!sortingColumn) {
        if (index === 0) {
            window.localStorage.setItem("filesSorting0", "lastModified");
            return "lastModified";
        } else if (index === 1) {
            window.localStorage.setItem("filesSorting1", "acl");
            return "acl";
        }
    }
    return sortingColumn;
};

export const initObject = (cloud: SDUCloud) => ({
    dashboard: {
        favoriteFiles: [] as File[],
        recentFiles: [] as File[],
        recentAnalyses: [] as Analysis[],
        activity: [] as any[],
        favoriteLoading: false,
        recentLoading: false,
        analysesLoading: false,
        activityLoading: false
    },
    files: {
        files: [] as File[],
        filesPerPage: 10,
        currentFilesPage: 0,
        loading: false,
        path: "",
        filesInfoPath: "",
        projects: [] as any[],
        sortingColumns: [getFilesSortingColumnOrDefault(0), getFilesSortingColumnOrDefault(1)],
        fileSelectorLoading: false,
        fileSelectorShown: false,
        fileSelectorFiles: [] as File[],
        fileSelectorPath: cloud.homeFolder,
        fileSelectorCallback: Function,
        disallowedPaths: []
    },
    uppy: {
        uppyFiles: initializeUppy({ maxNumberOfFiles: false } as UppyRestriction, cloud),
        uppyFilesOpen: false,
        uppyRunApp: initializeUppy({ maxNumberOfFiles: 1 } as UppyRestriction, cloud),
        uppyRunAppOpen: false
    },
    status: {
        status: DefaultStatus,
        title: ""
    },
    applications: {
        applications: [] as Application[],
        loading: false,
        applicationsPerPage: 10,
        currentApplicationsPage: 0
    },
    analyses: {
        loading: false,
        analyses: [] as Analysis[],
        analysesPerPage: 10,
        pageNumber: 0,
        totalPages: 0
    },
    zenodo: {
        loading: false,
        connected: false,
        publications: [] as Publication[]
    },
    sidebar: {
        open: false,
        loading: false,
        options: [] as SidebarOption[]
    }
});

export const identifierTypes = [
    {
        text: "Cited by",
        value: "isCitedBy"
    },
    {
        text: "Cites",
        value: "cites"
    },
    {
        text: "Supplement to",
        value: "isSupplementTo"
    },
    {
        text: "Supplemented by",
        value: "“isSupplementedBy”"
    },
    {
        text: "New version of",
        value: "isNewVersionOf"
    },
    {
        text: "Previous version of",
        value: "isPreviousVersionOf"
    },
    {
        text: "Part of",
        value: "“isPartOf”"
    },
    {
        text: "Has part",
        value: "“hasPart”"
    },
    {
        text: "Compiles",
        value: "compiles"
    },
    {
        text: "Is compiled by",
        value: "isCompiledBy"
    },
    {
        text: "Identical to",
        value: "isIdenticalTo"
    },
    {
        text: "Alternative identifier",
        value: "IsAlternateIdentifier"
    }
]

export const licenseOptions = [
    {
        text: "BSD Zero Clause License",
        value: "0BSD"
    },
    {
        text: "Attribution Assurance License",
        value: "AAL"
    },
    {
        text: "Abstyles License",
        value: "Abstyles"
    },
    {
        text: "Adobe Systems Incorporated Source Code License Agreement",
        value: "Adobe-2006"
    },
    {
        text: "Adobe Glyph List License",
        value: "Adobe-Glyph"
    },
    {
        text: "Amazon Digital Services License",
        value: "ADSL"
    },
    {
        text: "Academic Free License v1.1",
        value: "AFL-1.1"
    },
    {
        text: "Academic Free License v1.2",
        value: "AFL-1.2"
    },
    {
        text: "Academic Free License v2.0",
        value: "AFL-2.0"
    },
    {
        text: "Academic Free License v2.1",
        value: "AFL-2.1"
    },
    {
        text: "Academic Free License v3.0",
        value: "AFL-3.0"
    },
    {
        text: "Afmparse License",
        value: "Afmparse"
    },
    {
        text: "Affero General Public License v1.0 only",
        value: "AGPL-1.0-only"
    },
    {
        text: "Affero General Public License v1.0 or later",
        value: "AGPL-1.0-or-later"
    },
    {
        text: "GNU Affero General Public License v3.0 only",
        value: "AGPL-3.0-only"
    },
    {
        text: "GNU Affero General Public License v3.0 or later",
        value: "AGPL-3.0-or-later"
    },
    {
        text: "Aladdin Free Public License",
        value: "Aladdin"
    },
    {
        text: "AMD's plpa_map.c License",
        value: "AMDPLPA"
    },
    {
        text: "Apple MIT License",
        value: "AML"
    },
    {
        text: "Academy of Motion Picture Arts and Sciences BSD",
        value: "AMPAS"
    },
    {
        text: "ANTLR Software Rights Notice",
        value: "ANTLR-PD"
    },
    {
        text: "Apache License 1.0",
        value: "Apache-1.0"
    },
    {
        text: "Apache License 1.1",
        value: "Apache-1.1"
    },
    {
        text: "Apache License 2.0",
        value: "Apache-2.0"
    },
    {
        text: "Adobe Postscript AFM License",
        value: "APAFML"
    },
    {
        text: "Adaptive Public License 1.0",
        value: "APL-1.0"
    },
    {
        text: "Apple Public Source License 1.0",
        value: "APSL-1.0"
    },
    {
        text: "Apple Public Source License 1.1",
        value: "APSL-1.1"
    },
    {
        text: "Apple Public Source License 1.2",
        value: "APSL-1.2"
    },
    {
        text: "Apple Public Source License 2.0",
        value: "APSL-2.0"
    },
    {
        text: "Artistic License 1.0 w/clause 8",
        value: "Artistic-1.0-cl8"
    },
    {
        text: "Artistic License 1.0 (Perl)",
        value: "Artistic-1.0-Perl"
    },
    {
        text: "Artistic License 1.0",
        value: "Artistic-1.0"
    },
    {
        text: "Artistic License 2.0",
        value: "Artistic-2.0"
    },
    {
        text: "Bahyph License",
        value: "Bahyph"
    },
    {
        text: "Barr License",
        value: "Barr"
    },
    {
        text: "Beerware License",
        value: "Beerware"
    },
    {
        text: "BitTorrent Open Source License v1.0",
        value: "BitTorrent-1.0"
    },
    {
        text: "BitTorrent Open Source License v1.1",
        value: "BitTorrent-1.1"
    },
    {
        text: "Borceux license",
        value: "Borceux"
    },
    {
        text: "BSD 1-Clause License",
        value: "BSD-1-Clause"
    },
    {
        text: "BSD 2-Clause FreeBSD License",
        value: "BSD-2-Clause-FreeBSD"
    },
    {
        text: "BSD 2-Clause NetBSD License",
        value: "BSD-2-Clause-NetBSD"
    },
    {
        text: "BSD-2-Clause Plus Patent License",
        value: "BSD-2-Clause-Patent"
    },
    {
        text: "BSD 2-Clause \"Simplified\" License",
        value: "BSD-2-Clause"
    },
    {
        text: "BSD with attribution",
        value: "BSD-3-Clause-Attribution"
    },
    {
        text: "BSD 3-Clause Clear License",
        value: "BSD-3-Clause-Clear"
    },
    {
        text: "Lawrence Berkeley National Labs BSD variant license",
        value: "BSD-3-Clause-LBNL"
    },
    {
        text: "BSD 3-Clause No Nuclear License 2014",
        value: "BSD-3-Clause-No-Nuclear-License-2014"
    },
    {
        text: "BSD 3-Clause No Nuclear License",
        value: "BSD-3-Clause-No-Nuclear-License"
    },
    {
        text: "BSD 3-Clause No Nuclear Warranty",
        value: "BSD-3-Clause-No-Nuclear-Warranty"
    },
    {
        text: "BSD 3-Clause \"New\" or \"Revised\" License",
        value: "BSD-3-Clause"
    },
    {
        text: "BSD-4-Clause (University of California-Specific)",
        value: "BSD-4-Clause-UC"
    },
    {
        text: "BSD 4-Clause \"Original\" or \"Old\" License",
        value: "BSD-4-Clause"
    },
    {
        text: "BSD Protection License",
        value: "BSD-Protection"
    },
    {
        text: "BSD Source Code Attribution",
        value: "BSD-Source-Code"
    },
    {
        text: "Boost Software License 1.0",
        value: "BSL-1.0"
    },
    {
        text: "bzip2 and libbzip2 License v1.0.5",
        value: "bzip2-1.0.5"
    },
    {
        text: "bzip2 and libbzip2 License v1.0.6",
        value: "bzip2-1.0.6"
    },
    {
        text: "Caldera License",
        value: "Caldera"
    },
    {
        text: "Computer Associates Trusted Open Source License 1.1",
        value: "CATOSL-1.1"
    },
    {
        text: "Creative Commons Attribution 1.0 Generic",
        value: "CC-BY-1.0"
    },
    {
        text: "Creative Commons Attribution 2.0 Generic",
        value: "CC-BY-2.0"
    },
    {
        text: "Creative Commons Attribution 2.5 Generic",
        value: "CC-BY-2.5"
    },
    {
        text: "Creative Commons Attribution 3.0 Unported",
        value: "CC-BY-3.0"
    },
    {
        text: "Creative Commons Attribution 4.0 International",
        value: "CC-BY-4.0"
    },
    {
        text: "Creative Commons Attribution Non Commercial 1.0 Generic",
        value: "CC-BY-NC-1.0"
    },
    {
        text: "Creative Commons Attribution Non Commercial 2.0 Generic",
        value: "CC-BY-NC-2.0"
    },
    {
        text: "Creative Commons Attribution Non Commercial 2.5 Generic",
        value: "CC-BY-NC-2.5"
    },
    {
        text: "Creative Commons Attribution Non Commercial 3.0 Unported",
        value: "CC-BY-NC-3.0"
    },
    {
        text: "Creative Commons Attribution Non Commercial 4.0 International",
        value: "CC-BY-NC-4.0"
    },
    {
        text: "Creative Commons Attribution Non Commercial No Derivatives 1.0 Generic",
        value: "CC-BY-NC-ND-1.0"
    },
    {
        text: "Creative Commons Attribution Non Commercial No Derivatives 2.0 Generic",
        value: "CC-BY-NC-ND-2.0"
    },
    {
        text: "Creative Commons Attribution Non Commercial No Derivatives 2.5 Generic",
        value: "CC-BY-NC-ND-2.5"
    },
    {
        text: "Creative Commons Attribution Non Commercial No Derivatives 3.0 Unported",
        value: "CC-BY-NC-ND-3.0"
    },
    {
        text: "Creative Commons Attribution Non Commercial No Derivatives 4.0 International",
        value: "CC-BY-NC-ND-4.0"
    },
    {
        text: "Creative Commons Attribution Non Commercial Share Alike 1.0 Generic",
        value: "CC-BY-NC-SA-1.0"
    },
    {
        text: "Creative Commons Attribution Non Commercial Share Alike 2.0 Generic",
        value: "CC-BY-NC-SA-2.0"
    },
    {
        text: "Creative Commons Attribution Non Commercial Share Alike 2.5 Generic",
        value: "CC-BY-NC-SA-2.5"
    },
    {
        text: "Creative Commons Attribution Non Commercial Share Alike 3.0 Unported",
        value: "CC-BY-NC-SA-3.0"
    },
    {
        text: "Creative Commons Attribution Non Commercial Share Alike 4.0 International",
        value: "CC-BY-NC-SA-4.0"
    },
    {
        text: "Creative Commons Attribution No Derivatives 1.0 Generic",
        value: "CC-BY-ND-1.0"
    },
    {
        text: "Creative Commons Attribution No Derivatives 2.0 Generic",
        value: "CC-BY-ND-2.0"
    },
    {
        text: "Creative Commons Attribution No Derivatives 2.5 Generic",
        value: "CC-BY-ND-2.5"
    },
    {
        text: "Creative Commons Attribution No Derivatives 3.0 Unported",
        value: "CC-BY-ND-3.0"
    },
    {
        text: "Creative Commons Attribution No Derivatives 4.0 International",
        value: "CC-BY-ND-4.0"
    },
    {
        text: "Creative Commons Attribution Share Alike 1.0 Generic",
        value: "CC-BY-SA-1.0"
    },
    {
        text: "Creative Commons Attribution Share Alike 2.0 Generic",
        value: "CC-BY-SA-2.0"
    },
    {
        text: "Creative Commons Attribution Share Alike 2.5 Generic",
        value: "CC-BY-SA-2.5"
    },
    {
        text: "Creative Commons Attribution Share Alike 3.0 Unported",
        value: "CC-BY-SA-3.0"
    },
    {
        text: "Creative Commons Attribution Share Alike 4.0 International",
        value: "CC-BY-SA-4.0"
    },
    {
        text: "Creative Commons Zero v1.0 Universal",
        value: "CC0-1.0"
    },
    {
        text: "Common Development and Distribution License 1.0",
        value: "CDDL-1.0"
    },
    {
        text: "Common Development and Distribution License 1.1",
        value: "CDDL-1.1"
    },
    {
        text: "Community Data License Agreement Permissive 1.0",
        value: "CDLA-Permissive-1.0"
    },
    {
        text: "Community Data License Agreement Sharing 1.0",
        value: "CDLA-Sharing-1.0"
    },
    {
        text: "CeCILL Free Software License Agreement v1.0",
        value: "CECILL-1.0"
    },
    {
        text: "CeCILL Free Software License Agreement v1.1",
        value: "CECILL-1.1"
    },
    {
        text: "CeCILL Free Software License Agreement v2.0",
        value: "CECILL-2.0"
    },
    {
        text: "CeCILL Free Software License Agreement v2.1",
        value: "CECILL-2.1"
    },
    {
        text: "CeCILL-B Free Software License Agreement",
        value: "CECILL-B"
    },
    {
        text: "CeCILL-C Free Software License Agreement",
        value: "CECILL-C"
    },
    {
        text: "Clarified Artistic License",
        value: "ClArtistic"
    },
    {
        text: "CNRI Jython License",
        value: "CNRI-Jython"
    },
    {
        text: "CNRI Python Open Source GPL Compatible License Agreement",
        value: "CNRI-Python-GPL-Compatible"
    },
    {
        text: "CNRI Python License",
        value: "CNRI-Python"
    },
    {
        text: "Condor Public License v1.1",
        value: "Condor-1.1"
    },
    {
        text: "Common Public Attribution License 1.0",
        value: "CPAL-1.0"
    },
    {
        text: "Common Public License 1.0",
        value: "CPL-1.0"
    },
    {
        text: "Code Project Open License 1.02",
        value: "CPOL-1.02"
    },
    {
        text: "Crossword License",
        value: "Crossword"
    },
    {
        text: "CrystalStacker License",
        value: "CrystalStacker"
    },
    {
        text: "CUA Office Public License v1.0",
        value: "CUA-OPL-1.0"
    },
    {
        text: "Cube License",
        value: "Cube"
    },
    {
        text: "curl License",
        value: "curl"
    },
    {
        text: "Deutsche Freie Software Lizenz",
        value: "D-FSL-1.0"
    },
    {
        text: "diffmark license",
        value: "diffmark"
    },
    {
        text: "DOC License",
        value: "DOC"
    },
    {
        text: "Dotseqn License",
        value: "Dotseqn"
    },
    {
        text: "DSDP License",
        value: "DSDP"
    },
    {
        text: "dvipdfm License",
        value: "dvipdfm"
    },
    {
        text: "Educational Community License v1.0",
        value: "ECL-1.0"
    },
    {
        text: "Educational Community License v2.0",
        value: "ECL-2.0"
    },
    {
        text: "Eiffel Forum License v1.0",
        value: "EFL-1.0"
    },
    {
        text: "Eiffel Forum License v2.0",
        value: "EFL-2.0"
    },
    {
        text: "eGenix.com Public License 1.1.0",
        value: "eGenix"
    },
    {
        text: "Entessa Public License v1.0",
        value: "Entessa"
    },
    {
        text: "Eclipse Public License 1.0",
        value: "EPL-1.0"
    },
    {
        text: "Eclipse Public License 2.0",
        value: "EPL-2.0"
    },
    {
        text: "Erlang Public License v1.1",
        value: "ErlPL-1.1"
    },
    {
        text: "EU DataGrid Software License",
        value: "EUDatagrid"
    },
    {
        text: "European Union Public License 1.0",
        value: "EUPL-1.0"
    },
    {
        text: "European Union Public License 1.1",
        value: "EUPL-1.1"
    },
    {
        text: "European Union Public License 1.2",
        value: "EUPL-1.2"
    },
    {
        text: "Eurosym License",
        value: "Eurosym"
    },
    {
        text: "Fair License",
        value: "Fair"
    },
    {
        text: "Frameworx Open License 1.0",
        value: "Frameworx-1.0"
    },
    {
        text: "FreeImage Public License v1.0",
        value: "FreeImage"
    },
    {
        text: "FSF All Permissive License",
        value: "FSFAP"
    },
    {
        text: "FSF Unlimited License",
        value: "FSFUL"
    },
    {
        text: "FSF Unlimited License (with License Retention)",
        value: "FSFULLR"
    },
    {
        text: "Freetype Project License",
        value: "FTL"
    },
    {
        text: "GNU Free Documentation License v1.1 only",
        value: "GFDL-1.1-only"
    },
    {
        text: "GNU Free Documentation License v1.1 or later",
        value: "GFDL-1.1-or-later"
    },
    {
        text: "GNU Free Documentation License v1.2 only",
        value: "GFDL-1.2-only"
    },
    {
        text: "GNU Free Documentation License v1.2 or later",
        value: "GFDL-1.2-or-later"
    },
    {
        text: "GNU Free Documentation License v1.3 only",
        value: "GFDL-1.3-only"
    },
    {
        text: "GNU Free Documentation License v1.3 or later",
        value: "GFDL-1.3-or-later"
    },
    {
        text: "Giftware License",
        value: "Giftware"
    },
    {
        text: "GL2PS License",
        value: "GL2PS"
    },
    {
        text: "3dfx Glide License",
        value: "Glide"
    },
    {
        text: "Glulxe License",
        value: "Glulxe"
    },
    {
        text: "gnuplot License",
        value: "gnuplot"
    },
    {
        text: "GNU General Public License v1.0 only",
        value: "GPL-1.0-only"
    },
    {
        text: "GNU General Public License v1.0 or later",
        value: "GPL-1.0-or-later"
    },
    {
        text: "GNU General Public License v2.0 only",
        value: "GPL-2.0-only"
    },
    {
        text: "GNU General Public License v2.0 or later",
        value: "GPL-2.0-or-later"
    },
    {
        text: "GNU General Public License v3.0 only",
        value: "GPL-3.0-only"
    },
    {
        text: "GNU General Public License v3.0 or later",
        value: "GPL-3.0-or-later"
    },
    {
        text: "gSOAP Public License v1.3b",
        value: "gSOAP-1.3b"
    },
    {
        text: "Haskell Language Report License",
        value: "HaskellReport"
    },
    {
        text: "Historical Permission Notice and Disclaimer",
        value: "HPND"
    },
    {
        text: "IBM PowerPC Initialization and Boot Software",
        value: "IBM-pibs"
    },
    {
        text: "ICU License",
        value: "ICU"
    },
    {
        text: "Independent JPEG Group License",
        value: "IJG"
    },
    {
        text: "ImageMagick License",
        value: "ImageMagick"
    },
    {
        text: "iMatix Standard Function Library Agreement",
        value: "iMatix"
    },
    {
        text: "Imlib2 License",
        value: "Imlib2"
    },
    {
        text: "Info-ZIP License",
        value: "Info-ZIP"
    },
    {
        text: "Intel ACPI Software License Agreement",
        value: "Intel-ACPI"
    },
    {
        text: "Intel Open Source License",
        value: "Intel"
    },
    {
        text: "Interbase Public License v1.0",
        value: "Interbase-1.0"
    },
    {
        text: "IPA Font License",
        value: "IPA"
    },
    {
        text: "IBM Public License v1.0",
        value: "IPL-1.0"
    },
    {
        text: "ISC License",
        value: "ISC"
    },
    {
        text: "JasPer License",
        value: "JasPer-2.0"
    },
    {
        text: "JSON License",
        value: "JSON"
    },
    {
        text: "Licence Art Libre 1.2",
        value: "LAL-1.2"
    },
    {
        text: "Licence Art Libre 1.3",
        value: "LAL-1.3"
    },
    {
        text: "Latex2e License",
        value: "Latex2e"
    },
    {
        text: "Leptonica License",
        value: "Leptonica"
    },
    {
        text: "GNU Library General Public License v2 only",
        value: "LGPL-2.0-only"
    },
    {
        text: "GNU Library General Public License v2 or later",
        value: "LGPL-2.0-or-later"
    },
    {
        text: "GNU Lesser General Public License v2.1 only",
        value: "LGPL-2.1-only"
    },
    {
        text: "GNU Lesser General Public License v2.1 or later",
        value: "LGPL-2.1-or-later"
    },
    {
        text: "GNU Lesser General Public License v3.0 only",
        value: "LGPL-3.0-only"
    },
    {
        text: "GNU Lesser General Public License v3.0 or later",
        value: "LGPL-3.0-or-later"
    },
    {
        text: "Lesser General Public License For Linguistic Resources",
        value: "LGPLLR"
    },
    {
        text: "libpng License",
        value: "Libpng"
    },
    {
        text: "libtiff License",
        value: "libtiff"
    },
    {
        text: "Licence Libre du Québec – Permissive version 1.1",
        value: "LiLiQ-P-1.1"
    },
    {
        text: "Licence Libre du Québec – Réciprocité version 1.1",
        value: "LiLiQ-R-1.1"
    },
    {
        text: "Licence Libre du Québec – Réciprocité forte version 1.1",
        value: "LiLiQ-Rplus-1.1"
    },
    {
        text: "Linux Kernel Variant of OpenIB.org license",
        value: "Linux-OpenIB"
    },
    {
        text: "Lucent Public License Version 1.0",
        value: "LPL-1.0"
    },
    {
        text: "Lucent Public License v1.02",
        value: "LPL-1.02"
    },
    {
        text: "LaTeX Project Public License v1.0",
        value: "LPPL-1.0"
    },
    {
        text: "LaTeX Project Public License v1.1",
        value: "LPPL-1.1"
    },
    {
        text: "LaTeX Project Public License v1.2",
        value: "LPPL-1.2"
    },
    {
        text: "LaTeX Project Public License v1.3a",
        value: "LPPL-1.3a"
    },
    {
        text: "LaTeX Project Public License v1.3c",
        value: "LPPL-1.3c"
    },
    {
        text: "MakeIndex License",
        value: "MakeIndex"
    },
    {
        text: "MirOS License",
        value: "MirOS"
    },
    {
        text: "MIT No Attribution",
        value: "MIT-0"
    },
    {
        text: "Enlightenment License (e16)",
        value: "MIT-advertising"
    },
    {
        text: "CMU License",
        value: "MIT-CMU"
    },
    {
        text: "enna License",
        value: "MIT-enna"
    },
    {
        text: "feh License",
        value: "MIT-feh"
    },
    {
        text: "MIT License",
        value: "MIT"
    },
    {
        text: "MIT +no-false-attribs license",
        value: "MITNFA"
    },
    {
        text: "Motosoto License",
        value: "Motosoto"
    },
    {
        text: "mpich2 License",
        value: "mpich2"
    },
    {
        text: "Mozilla Public License 1.0",
        value: "MPL-1.0"
    },
    {
        text: "Mozilla Public License 1.1",
        value: "MPL-1.1"
    },
    {
        text: "Mozilla Public License 2.0 (no copyleft exception)",
        value: "MPL-2.0-no-copyleft-exception"
    },
    {
        text: "Mozilla Public License 2.0",
        value: "MPL-2.0"
    },
    {
        text: "Microsoft Public License",
        value: "MS-PL"
    },
    {
        text: "Microsoft Reciprocal License",
        value: "MS-RL"
    },
    {
        text: "Matrix Template Library License",
        value: "MTLL"
    },
    {
        text: "Multics License",
        value: "Multics"
    },
    {
        text: "Mup License",
        value: "Mup"
    },
    {
        text: "NASA Open Source Agreement 1.3",
        value: "NASA-1.3"
    },
    {
        text: "Naumen Public License",
        value: "Naumen"
    },
    {
        text: "Net Boolean Public License v1",
        value: "NBPL-1.0"
    },
    {
        text: "University of Illinois/NCSA Open Source License",
        value: "NCSA"
    },
    {
        text: "Net-SNMP License",
        value: "Net-SNMP"
    },
    {
        text: "NetCDF license",
        value: "NetCDF"
    },
    {
        text: "Newsletr License",
        value: "Newsletr"
    },
    {
        text: "Nethack General Public License",
        value: "NGPL"
    },
    {
        text: "Norwegian Licence for Open Government Data",
        value: "NLOD-1.0"
    },
    {
        text: "No Limit Public License",
        value: "NLPL"
    },
    {
        text: "Nokia Open Source License",
        value: "Nokia"
    },
    {
        text: "Netizen Open Source License",
        value: "NOSL"
    },
    {
        text: "Noweb License",
        value: "Noweb"
    },
    {
        text: "Netscape Public License v1.0",
        value: "NPL-1.0"
    },
    {
        text: "Netscape Public License v1.1",
        value: "NPL-1.1"
    },
    {
        text: "Non-Profit Open Software License 3.0",
        value: "NPOSL-3.0"
    },
    {
        text: "NRL License",
        value: "NRL"
    },
    {
        text: "NTP License",
        value: "NTP"
    },
    {
        text: "Open CASCADE Technology Public License",
        value: "OCCT-PL"
    },
    {
        text: "OCLC Research Public License 2.0",
        value: "OCLC-2.0"
    },
    {
        text: "ODC Open Database License v1.0",
        value: "ODbL-1.0"
    },
    {
        text: "SIL Open Font License 1.0",
        value: "OFL-1.0"
    },
    {
        text: "SIL Open Font License 1.1",
        value: "OFL-1.1"
    },
    {
        text: "Open Group Test Suite License",
        value: "OGTSL"
    },
    {
        text: "Open LDAP Public License v1.1",
        value: "OLDAP-1.1"
    },
    {
        text: "Open LDAP Public License v1.2",
        value: "OLDAP-1.2"
    },
    {
        text: "Open LDAP Public License v1.3",
        value: "OLDAP-1.3"
    },
    {
        text: "Open LDAP Public License v1.4",
        value: "OLDAP-1.4"
    },
    {
        text: "Open LDAP Public License v2.0.1",
        value: "OLDAP-2.0.1"
    },
    {
        text: "Open LDAP Public License v2.0 (or possibly 2.0A and 2.0B)",
        value: "OLDAP-2.0"
    },
    {
        text: "Open LDAP Public License v2.1",
        value: "OLDAP-2.1"
    },
    {
        text: "Open LDAP Public License v2.2.1",
        value: "OLDAP-2.2.1"
    },
    {
        text: "Open LDAP Public License 2.2.2",
        value: "OLDAP-2.2.2"
    },
    {
        text: "Open LDAP Public License v2.2",
        value: "OLDAP-2.2"
    },
    {
        text: "Open LDAP Public License v2.3",
        value: "OLDAP-2.3"
    },
    {
        text: "Open LDAP Public License v2.4",
        value: "OLDAP-2.4"
    },
    {
        text: "Open LDAP Public License v2.5",
        value: "OLDAP-2.5"
    },
    {
        text: "Open LDAP Public License v2.6",
        value: "OLDAP-2.6"
    },
    {
        text: "Open LDAP Public License v2.7",
        value: "OLDAP-2.7"
    },
    {
        text: "Open LDAP Public License v2.8",
        value: "OLDAP-2.8"
    },
    {
        text: "Open Market License",
        value: "OML"
    },
    {
        text: "OpenSSL License",
        value: "OpenSSL"
    },
    {
        text: "Open Public License v1.0",
        value: "OPL-1.0"
    },
    {
        text: "OSET Public License version 2.1",
        value: "OSET-PL-2.1"
    },
    {
        text: "Open Software License 1.0",
        value: "OSL-1.0"
    },
    {
        text: "Open Software License 1.1",
        value: "OSL-1.1"
    },
    {
        text: "Open Software License 2.0",
        value: "OSL-2.0"
    },
    {
        text: "Open Software License 2.1",
        value: "OSL-2.1"
    },
    {
        text: "Open Software License 3.0",
        value: "OSL-3.0"
    },
    {
        text: "ODC Public Domain Dedication & License 1.0",
        value: "PDDL-1.0"
    },
    {
        text: "PHP License v3.0",
        value: "PHP-3.0"
    },
    {
        text: "PHP License v3.01",
        value: "PHP-3.01"
    },
    {
        text: "Plexus Classworlds License",
        value: "Plexus"
    },
    {
        text: "PostgreSQL License",
        value: "PostgreSQL"
    },
    {
        text: "psfrag License",
        value: "psfrag"
    },
    {
        text: "psutils License",
        value: "psutils"
    },
    {
        text: "Python License 2.0",
        value: "Python-2.0"
    },
    {
        text: "Qhull License",
        value: "Qhull"
    },
    {
        text: "Q Public License 1.0",
        value: "QPL-1.0"
    },
    {
        text: "Rdisc License",
        value: "Rdisc"
    },
    {
        text: "Red Hat eCos Public License v1.1",
        value: "RHeCos-1.1"
    },
    {
        text: "Reciprocal Public License 1.1",
        value: "RPL-1.1"
    },
    {
        text: "Reciprocal Public License 1.5",
        value: "RPL-1.5"
    },
    {
        text: "RealNetworks Public Source License v1.0",
        value: "RPSL-1.0"
    },
    {
        text: "RSA Message-Digest License",
        value: "RSA-MD"
    },
    {
        text: "Ricoh Source Code Public License",
        value: "RSCPL"
    },
    {
        text: "Ruby License",
        value: "Ruby"
    },
    {
        text: "Sax Public Domain Notice",
        value: "SAX-PD"
    },
    {
        text: "Saxpath License",
        value: "Saxpath"
    },
    {
        text: "SCEA Shared Source License",
        value: "SCEA"
    },
    {
        text: "Sendmail License",
        value: "Sendmail"
    },
    {
        text: "SGI Free Software License B v1.0",
        value: "SGI-B-1.0"
    },
    {
        text: "SGI Free Software License B v1.1",
        value: "SGI-B-1.1"
    },
    {
        text: "SGI Free Software License B v2.0",
        value: "SGI-B-2.0"
    },
    {
        text: "Simple Public License 2.0",
        value: "SimPL-2.0"
    },
    {
        text: "Sun Industry Standards Source License v1.2",
        value: "SISSL-1.2"
    },
    {
        text: "Sun Industry Standards Source License v1.1",
        value: "SISSL"
    },
    {
        text: "Sleepycat License",
        value: "Sleepycat"
    },
    {
        text: "Standard ML of New Jersey License",
        value: "SMLNJ"
    },
    {
        text: "Secure Messaging Protocol Public License",
        value: "SMPPL"
    },
    {
        text: "SNIA Public License 1.1",
        value: "SNIA"
    },
    {
        text: "Spencer License 86",
        value: "Spencer-86"
    },
    {
        text: "Spencer License 94",
        value: "Spencer-94"
    },
    {
        text: "Spencer License 99",
        value: "Spencer-99"
    },
    {
        text: "Sun Public License v1.0",
        value: "SPL-1.0"
    },
    {
        text: "SugarCRM Public License v1.1.3",
        value: "SugarCRM-1.1.3"
    },
    {
        text: "Scheme Widget Library (SWL) Software License Agreement",
        value: "SWL"
    },
    {
        text: "TCL/TK License",
        value: "TCL"
    },
    {
        text: "TCP Wrappers License",
        value: "TCP-wrappers"
    },
    {
        text: "TMate Open Source License",
        value: "TMate"
    },
    {
        text: "TORQUE v2.5+ Software License v1.1",
        value: "TORQUE-1.1"
    },
    {
        text: "Trusster Open Source License",
        value: "TOSL"
    },
    {
        text: "Unicode License Agreement - Data Files and Software (2015)",
        value: "Unicode-DFS-2015"
    },
    {
        text: "Unicode License Agreement - Data Files and Software (2016)",
        value: "Unicode-DFS-2016"
    },
    {
        text: "Unicode Terms of Use",
        value: "Unicode-TOU"
    },
    {
        text: "The Unlicense",
        value: "Unlicense"
    },
    {
        text: "Universal Permissive License v1.0",
        value: "UPL-1.0"
    },
    {
        text: "Vim License",
        value: "Vim"
    },
    {
        text: "VOSTROM Public License for Open Source",
        value: "VOSTROM"
    },
    {
        text: "Vovida Software License v1.0",
        value: "VSL-1.0"
    },
    {
        text: "W3C Software Notice and License (1998-07-20)",
        value: "W3C-19980720"
    },
    {
        text: "W3C Software Notice and Document License (2015-05-13)",
        value: "W3C-20150513"
    },
    {
        text: "W3C Software Notice and License (2002-12-31)",
        value: "W3C"
    },
    {
        text: "Sybase Open Watcom Public License 1.0",
        value: "Watcom-1.0"
    },
    {
        text: "Wsuipa License",
        value: "Wsuipa"
    },
    {
        text: "Do What The F*ck You Want To Public License",
        value: "WTFPL"
    },
    {
        text: "X11 License",
        value: "X11"
    },
    {
        text: "Xerox License",
        value: "Xerox"
    },
    {
        text: "XFree86 License 1.1",
        value: "XFree86-1.1"
    },
    {
        text: "xinetd License",
        value: "xinetd"
    },
    {
        text: "X.Net License",
        value: "Xnet"
    },
    {
        text: "XPP License",
        value: "xpp"
    },
    {
        text: "XSkat License",
        value: "XSkat"
    },
    {
        text: "Yahoo! Public License v1.0",
        value: "YPL-1.0"
    },
    {
        text: "Yahoo! Public License v1.1",
        value: "YPL-1.1"
    },
    {
        text: "Zed License",
        value: "Zed"
    },
    {
        text: "Zend License v2.0",
        value: "Zend-2.0"
    },
    {
        text: "Zimbra Public License v1.3",
        value: "Zimbra-1.3"
    },
    {
        text: "Zimbra Public License v1.4",
        value: "Zimbra-1.4"
    },
    {
        text: "zlib/libpng License with Acknowledgement",
        value: "zlib-acknowledgement"
    },
    {
        text: "zlib License",
        value: "Zlib"
    },
    {
        text: "Zope Public License 1.1",
        value: "ZPL-1.1"
    },
    {
        text: "Zope Public License 2.0",
        value: "ZPL-2.0"
    },
    {
        text: "Zope Public License 2.1",
        value: "ZPL-2.1"
    }
]