import * as React from "react";
import {useNavigate} from "react-router";
import {useLayoutEffect, useRef} from "react";
import MainContainer from "@/ui-components/MainContainer";
import {
    EmptyReasonTag,
    placeholderImage,
    ResourceBrowseFeatures,
    ResourceBrowser,
    ColumnTitleList,
    Selection,
} from "@/ui-components/ResourceBrowser";
import {fileName} from "@/Utilities/FileUtilities";
import {
    bulkRequestOf,
    extensionFromPath,
    extensionType,
} from "@/UtilityFunctions";
import {IconName} from "@/ui-components/Icon";
import {ThemeColor} from "@/ui-components/theme";
import {SvgFt} from "@/ui-components/FtIcon";
import {getCssPropertyValue} from "@/Utilities/StylingUtilities";
import MetadataDocumentApi, {FileMetadataAttached} from "@/UCloud/MetadataDocumentApi";
import AppRoutes from "@/Routes";
import {image} from "@/Utilities/HTMLUtilities";
import {isLikelyFolder, sidebarFavoriteCache} from "./FavoriteCache";
import {callAPI} from "@/Authentication/DataHook";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import {UFile} from "@/UCloud/UFile";
import {FileIconHint} from ".";

const FEATURES: ResourceBrowseFeatures = {
    dragToSelect: true,
    showStar: true,
    renderSpinnerWhenLoading: true,
    showHeaderInEmbedded: true,
    showColumnTitles: true,
    breadcrumbsSeparatedBySlashes: false,
}

type SortById = "PATH" | "MODIFIED_AT" | "SIZE";
const rowTitles: ColumnTitleList<SortById> = [{name: "Name"}, {name: "", columnWidth: 32}, {name: "", columnWidth: 0}, {name: "", columnWidth: 0}];

function FavoriteBrowse({selection, navigateToFolder}: {navigateToFolder: (path: string) => void; selection: Selection<FileMetadataAttached | UFile>}): React.ReactNode {
    const navigate = useNavigate();
    const mountRef = useRef<HTMLDivElement | null>(null);
    const browserRef = useRef<ResourceBrowser<FileMetadataAttached> | null>(null);

    const favorites = React.useSyncExternalStore(s => sidebarFavoriteCache.subscribe(s), () => sidebarFavoriteCache.getSnapshot());

    React.useEffect(() => {
        if (browserRef.current) {
            browserRef.current.registerPage(favorites, "/", true);
            browserRef.current.renderRows();

            sidebarFavoriteCache.fetchFileInfo(favorites.items.map(it => it.path)).then(() => {
                browserRef.current?.renderRows();
            });
        }
    }, [browserRef.current, favorites]);

    useLayoutEffect(() => {
        const mount = mountRef.current;
        if (mount && !browserRef.current) {
            new ResourceBrowser<FileMetadataAttached>(mount, "Favorites", {isModal: true}).init(browserRef, FEATURES, "/", browser => {
                browser.setColumns(rowTitles);

                browser.on("fetchFilters", () => []);

                browser.on("fetchOperations", () => []);

                browser.on("fetchOperationsCallback", () => ({isModal: true, embedded: true}));

                // Rendering of rows and empty pages
                // =========================================================================================================
                const renderFileIconFromProperties = async (
                    extension: string,
                    isDirectory: boolean,
                    hint?: FileIconHint
                ): Promise<string> => {
                    const hasExt = !!extension;
                    const type = extension ? extensionType(extension.toLocaleLowerCase()) : "binary";

                    const width = 64;
                    const height = 64;

                    if (hint || isDirectory) {
                        let name: IconName;
                        let color: ThemeColor = "FtFolderColor";
                        let color2: ThemeColor = "FtFolderColor2";
                        switch (hint) {
                            case "DIRECTORY_JOBS":
                                name = "ftResultsFolder";
                                break;
                            case "DIRECTORY_SHARES":
                                name = "ftSharesFolder";
                                break;
                            case "DIRECTORY_STAR":
                                name = "ftFavFolder";
                                break;
                            case "DIRECTORY_TRASH":
                                color = "errorMain";
                                color2 = "errorLight";
                                name = "trash";
                                break;
                            default:
                                name = "ftFolder";
                                break;
                        }

                        return ResourceBrowser.icons.renderIcon({name, color, color2, width, height});
                    }

                    return ResourceBrowser.icons.renderSvg(
                        "file-" + extension,
                        () => <SvgFt color={getCssPropertyValue("FtIconColor")} color2={getCssPropertyValue("FtIconColor2")} hasExt={hasExt}
                            ext={extension} type={type} width={width} height={height} />,
                        width,
                        height
                    );
                };

                const renderFileIcon = (filePath: string): Promise<string> => {
                    const ext = filePath.indexOf(".") !== -1 ? extensionFromPath(filePath) : undefined;
                    const ext4 = ext?.substring(0, 4) ?? "File";
                    const fileInfo = sidebarFavoriteCache.fileInfoIfPresent(filePath);
                    return renderFileIconFromProperties(ext4, fileInfo?.status.type === "DIRECTORY" ?? isLikelyFolder(filePath), fileInfo?.status.icon);
                };

                browser.on("renderRow", (fav, row, containerWidth) => {
                    const fileInfo = sidebarFavoriteCache.fileInfoIfPresent(fav.path);
                    const [icon, setIcon] = ResourceBrowser.defaultIconRenderer();
                    renderFileIcon(fav.path).then(setIcon)
                    row.title.append(icon);

                    const title = ResourceBrowser.defaultTitleRenderer(fileName(fav.path), containerWidth, row);
                    row.title.append(title);

                    // FIXME(Jonas): Duplicated
                    // TODO(Dan): This seems like it might be useful in more places than just the file browser
                    const favoriteIcon = image(placeholderImage, {width: 20, height: 20, alt: "Star"});
                    {
                        row.star.innerHTML = "";
                        row.star.style.minWidth = "20px"
                        row.star.append(favoriteIcon);
                        row.star.style.cursor = "pointer";
                        row.star.style.marginRight = "8px";
                    }

                    ResourceBrowser.icons.renderIcon({
                        name: "starFilled",
                        color: "favoriteColor",
                        color2: "iconColor2",
                        height: 64,
                        width: 64
                    }).then(icon => favoriteIcon.src = icon);

                    row.star.setAttribute("data-favorite", "true");

                    const button = browser.defaultButtonRenderer(selection, fileInfo ?? fav);
                    if (button) {
                        row.stat3.append(button);
                    }
                });

                ResourceBrowser.icons.renderIcon({
                    name: "ftFolder",
                    color: "FtFolderColor",
                    color2: "FtFolderColor2",
                    height: 256,
                    width: 256
                }).then(icon => {
                    const fragment = document.createDocumentFragment();
                    fragment.append(image(icon, {height: 60, width: 60}));
                    browser.defaultEmptyGraphic = fragment;
                });

                browser.on("renderEmptyPage", reason => {
                    // NOTE(Dan): The reasons primarily come from the prefetch() function which fetches the data. If you
                    // want to recognize new error codes, then you should add the logic in prefetch() first.
                    const e = browser.emptyPageElement;
                    switch (reason.tag) {
                        case EmptyReasonTag.LOADING: {
                            e.reason.append("We are fetching your favorites...");
                            break;
                        }

                        case EmptyReasonTag.EMPTY: {
                            e.reason.append("No favorites found");
                            break;
                        }

                        case EmptyReasonTag.NOT_FOUND_OR_NO_PERMISSIONS: {
                            e.reason.append("We could not find any favorites.");
                            e.providerReason.append(reason.information ?? "");
                            break;
                        }

                        case EmptyReasonTag.UNABLE_TO_FULFILL: {
                            e.reason.append("Favorites are currently unavailable, try again later.");
                            e.providerReason.append(reason.information ?? "");
                            break;
                        }
                    }

                    if (reason.information === "Invalid file type") {
                        navigate(AppRoutes.files.preview(browser.currentPath));
                    }
                });

                // Rendering of breadcrumbs and the location bar
                // =========================================================================================================
                browser.on("generateBreadcrumbs", () => browser.defaultBreadcrumbs());
                browser.on("skipOpen", (oldPath, newPath, resource) => {
                    const pathInfo = sidebarFavoriteCache.fileInfoIfPresent(oldPath);
                    if (!pathInfo) return true;
                    else return pathInfo.status.type === "FILE";
                });

                browser.on("open", (oldPath, newPath, resource) => {
                    if (resource) {
                        navigateToFolder(newPath);
                    }
                });

                browser.on("wantToFetchNextPage", async (path) => {
                    // TODO(Jonas)
                    // browser.registerPage(result, path, false);
                });

                browser.on("fetchBrowserFeatures", () => {
                    return [{name: "Rename", shortcut: {keys: "F2"}}];
                });

                // Event handlers related to user input
                // =========================================================================================================
                // This section includes handlers for clicking various UI elements and handling shortcuts.
                browser.on("unhandledShortcut", (ev) => {});

                browser.on("starClicked", (entry) => {
                    (async () => {
                        try {
                            callAPI(MetadataDocumentApi.delete(
                                bulkRequestOf({
                                    changeLog: "Removed favorite",
                                    id: entry.metadata.id
                                })
                            ));
                            sidebarFavoriteCache.remove(entry.path);
                        } catch (e) {
                            snackbarStore.addFailure("Failed to remove favorite", false)
                        }
                    })();
                });


                // Utilities required for the ResourceBrowser to understand the structure of the file-system
                // =========================================================================================================
                // This usually includes short functions which describe when certain actions should take place and what
                // the internal structure of a file is.
                browser.on("pathToEntry", f => f.path);
                browser.on("nameOfEntry", f => fileName(f.path));
            });
        }
    }, []);


    // Note(Jonas): I assume this will always be a selection modal, so no refresh function

    return <MainContainer
        main={<div ref={mountRef} />}
    />;
}

export default FavoriteBrowse;