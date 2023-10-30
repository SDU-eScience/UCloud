import {useCloudCommand} from "@/Authentication/DataHook";
import * as React from "react";
import {ExtensionType, extensionFromPath, extensionType, isLightThemeStored, languageFromExtension, typeFromMime} from "@/UtilityFunctions";
import {PredicatedLoadingSpinner} from "@/LoadingIcon/LoadingIcon";
import {Markdown} from "@/ui-components";
import {api as FilesApi, FilesCreateDownloadResponseItem, normalizeDownloadEndpoint, UFile} from "@/UCloud/FilesApi";
import {useEffect, useState} from "react";
import {fileName} from "@/Utilities/FileUtilities";
import {bulkRequestOf} from "@/DefaultObjects";
import {BulkResponse} from "@/UCloud";
import {injectStyle} from "@/Unstyled";
import fileType from "magic-bytes.js";
import Editor from "@monaco-editor/react";
import {PREVIEW_MAX_SIZE} from "../../site.config.json";

export const MAX_PREVIEW_SIZE_IN_BYTES = PREVIEW_MAX_SIZE;

export const FilePreview: React.FunctionComponent<{file: UFile}> = ({file}) => {
    const [type, setType] = useState<ExtensionType>(null);
    const [loading, invokeCommand] = useCloudCommand();

    const [data, setData] = useState("");
    const [error, setError] = useState<string | null>(null);

    const fetchData = React.useCallback(async () => {
        const size = file.status.sizeInBytes;
        if (file.status.type !== "FILE") return;
        if (!loading && size != null && size < MAX_PREVIEW_SIZE_IN_BYTES && size > 0) {
            try {
                const download = await invokeCommand<BulkResponse<FilesCreateDownloadResponseItem>>(
                    FilesApi.createDownload(bulkRequestOf({id: file.id})),
                    {defaultErrorHandler: false}
                );
                const downloadEndpoint = download?.responses[0]?.endpoint.replace("integration-module:8889", "localhost:9000");
                if (!downloadEndpoint) {
                    setError("Unable to display preview. Try again later or with a different file.");
                    return;
                }
                const content = await fetch(normalizeDownloadEndpoint(downloadEndpoint));
                const contentBlob = await content.blob();
                const contentBuffer = new Uint8Array(await contentBlob.arrayBuffer());

                const foundFileType = fileType(contentBuffer);
                if (foundFileType.length === 0) {
                    const text = tryDecodeText(contentBuffer);
                    if (text !== null) {
                        setType("text");
                        setData(await contentBlob.text());
                        setError(null);
                    } else {
                        setError("Preview is not supported for this file.");
                    }
                } else {
                    const typeFromFileType = typeFromMime(foundFileType[0].mime ?? "") ?? extensionType(foundFileType[0].typename);

                    switch (typeFromFileType) {
                        case "image":
                        case "audio":
                        case "video":
                        case "pdf":
                            setData(URL.createObjectURL(new Blob([contentBlob], {type: foundFileType[0].mime}))); 
                            setError(null);
                            break;
                        case "code":
                        case "text":
                        case "application":
                        case "markdown":
                            setData(await content.text());
                            setError(null);
                            break;
                        default:
                            setError(`Preview not supported for '${foundFileType[0].typename}'.`);
                            break;
                    }

                    setType(typeFromFileType);
                }
            } catch (e) {
                setError("Unable to display preview. Try again later or with a different file.");
            }
        } else if (size != null && size >= MAX_PREVIEW_SIZE_IN_BYTES) {
            setError("File is too large to preview");
        } else if (!size || size <= 0) {
            setError("File is empty");
        } else {
            setError("Preview is not supported for this file.");
        }
    }, [file]);

    useEffect(() => {
        fetchData();
    }, [file]);

    if (file.status.type !== "FILE") return null;
    if ((loading || data === "") && !error) return <PredicatedLoadingSpinner loading />

    let node: JSX.Element | null;

    switch (type) {
        case "text":
        case "code":
            node = <Editor
                height="500px"
                width="100%"
                options={{
                    readOnly: true,
                    minimap: {enabled: false},
                }}
                theme={isLightThemeStored() ? "light" : "vs-dark"}
                language={languageFromExtension(extensionFromPath(file.id))}
                className="fullscreen"
                value={data}
            />;
            break;
        case "image":
            node = <img alt={fileName(file.id)} src={data} />
            break;
        case "audio":
            node = <audio controls src={data} />;
            break;
        case "video":
            node = <video src={data} controls />;
            break;
        case "pdf":
            node = <object type="application/pdf" style={{width: "100vw", height: "100vh"}} className="fullscreen" data={data} />;
            break;
        case "markdown":
            node = <div><Markdown>{data}</Markdown></div>;
            break;
        default:
            node = <div />
            break;
    }
    
    if (error !== null) {
        node = <div>{error}</div>;
    }

    return <div className={ItemWrapperClass}>{node}</div>;
}

const ItemWrapperClass = injectStyle("item-wrapper", k => `
    ${k} {
        display: flex;
        justify-content: center;
        margin-bottom: 15px;
    }

    ${k} > * {
      max-width: 100%;
      max-height: calc(100vh - 300px);
      overflow-y: scroll;
    }
`);

function tryDecodeText(buf: Uint8Array): string | null {
    try {
        const d = new TextDecoder("utf-8", { fatal: true });
        return d.decode(buf);
    } catch (e) {
        return null;
    }
}