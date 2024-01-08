import * as React from "react";
import {useCloudCommand} from "@/Authentication/DataHook";
import {useCallback, useEffect, useState} from "react";
import {default as metadataApi} from "@/UCloud/MetadataDocumentApi";
import {default as metadataNsApi, FileMetadataTemplateNamespace} from "@/UCloud/MetadataNamespaceApi";
import {bulkRequestOf} from "@/DefaultObjects";
import {Icon} from "@/ui-components";
import {PageV2} from "@/UCloud";
import {UFile} from "@/UCloud/UFile";

let favoriteTemplateId: string | undefined;
const favoriteTemplateVersion = "1.0.0";

export const FileFavoriteToggle: React.FunctionComponent<{
    file?: UFile,
}> = ({file}) => {
    const [, invokeCommand, loading] = useCloudCommand();
    const [isFavorite, setFavorite] = useState(false);
    const [mostRecentStatusId, setStatusId] = useState("");

    useEffect(() => {
        // NOTE(Dan): This should only run once on mount.
        let realFileIsFavorite = false;
        if (file) {
            const metadata = file.status.metadata;
            if (!favoriteTemplateId && metadata) {
                const favoriteTemplate =
                    Object.values(metadata.templates).find(it => it.namespaceName === "favorite");
                if (favoriteTemplate) favoriteTemplateId = favoriteTemplate.namespaceId;
            }

            if (metadata && favoriteTemplateId) {
                const mostRecent = metadata.metadata[favoriteTemplateId]?.[0];
                realFileIsFavorite = mostRecent != null && mostRecent.type === "metadata" &&
                    mostRecent.specification.version === favoriteTemplateVersion &&
                    mostRecent.specification.document["favorite"];
                if (mostRecent) setStatusId(mostRecent.id);
            }
        }

        setFavorite(realFileIsFavorite);
    }, []);


    const onToggle = useCallback(async (e?: React.SyntheticEvent) => {
        e?.stopPropagation();
        if (!file) return; // NOTE(Dan): If the star is clicked on a placeholder file don't do anything.
        if (loading.current) return;

        setFavorite(!isFavorite);
        try {
            if (!favoriteTemplateId) {
                const page = await invokeCommand<PageV2<FileMetadataTemplateNamespace>>(
                    metadataNsApi.browse(({filterName: "favorite", itemsPerPage: 50}))
                );
                const ns = page?.items?.[0];
                if (ns) {
                    favoriteTemplateId = ns.id;
                }
            }

            if (!favoriteTemplateId) {
                setFavorite(isFavorite);
                return;
            }

            if (isFavorite) {
                // Note(Jonas): New state will be _not_  favorite

                await invokeCommand(
                    metadataApi.delete(
                        bulkRequestOf({
                            changeLog: "Remove favorite",
                            id: mostRecentStatusId
                        })
                    ),
                    {defaultErrorHandler: false}
                );
            } else {
                // Note(Jonas): New state will be favorite
                setStatusId((await invokeCommand(
                    /* Note(Jonas): If already favorite, new */
                    metadataApi.create(bulkRequestOf({
                        fileId: file.id,
                        metadata: {
                            document: {favorite: !isFavorite},
                            version: favoriteTemplateVersion,
                            changeLog: "New favorite status",
                            templateId: favoriteTemplateId!
                        }
                    })),
                    {defaultErrorHandler: false}
                )).responses[0].id);
            }


        } catch (e) {
            setFavorite(isFavorite);
        }
    }, [file, isFavorite, mostRecentStatusId]);

    return <Icon
        name={isFavorite ? "starFilled" : "starEmpty"} mr={"10px"} color={isFavorite ? "blue" : "midGray"}
        cursor={"pointer"} onClick={onToggle} />;
};
