import * as React from "react";
import * as UCloud from "UCloud";
import templateApi = UCloud.file.orchestrator.metadata_template;
import {useHistory} from "react-router";
import {getQueryParam} from "Utilities/URIUtilities";
import {useCloudAPI, useCloudCommand} from "Authentication/DataHook";
import {useCallback, useEffect} from "react";
import {file} from "UCloud";
import FileMetadataTemplate = file.orchestrator.FileMetadataTemplate;
import {aclOptions, entityName} from "Files/Metadata/Templates/Browse";
import {useLoading, useTitle} from "Navigation/Redux/StatusActions";
import {useRefreshFunction} from "Navigation/Redux/HeaderActions";
import {SidebarPages, useSidebarPage} from "ui-components/Sidebar";
import MainContainer from "MainContainer/MainContainer";
import {Section} from "ui-components/Section";
import * as Heading from "ui-components/Heading";
import HexSpin from "LoadingIcon/LoadingIcon";
import {ResourcePage} from "ui-components/ResourcePage";
import {prettierString} from "UtilityFunctions";
import { JsonSchemaForm } from "../JsonSchemaForm";

const Properties: React.FunctionComponent = props => {
    const history = useHistory();
    const id = getQueryParam(history.location.search, "id");
    const [template, fetchTemplate] = useCloudAPI<FileMetadataTemplate | null>({noop: true}, null);
    const [commandLoading, invokeCommand] = useCloudCommand();

    const reload = useCallback(() => {
        fetchTemplate(templateApi.retrieve({id: id ?? "?"}));
    }, [id]);

    useEffect(reload, [reload]);

    {
        let title = entityName;
        if (template?.data) {
            title += ` (${template.data.specification.title})`;
        }
        useTitle(title);
        useLoading(template.loading);
        useRefreshFunction(reload);
        useSidebarPage(SidebarPages.Files);
    }

    let main: JSX.Element;
    if (template.error) {
        main = <>{template.error.statusCode}: {template.error.why}</>
    } else if (template.loading || !template.data) {
        main = <HexSpin />;
    } else {
        const t = template.data!;
        main = <ResourcePage
            entityName={entityName}
            aclOptions={aclOptions}
            stats={[
                {
                    title: "Title",
                    inline: true,
                    render: () => t.specification.title
                },
                {
                    title: "Version",
                    inline: true,
                    render: () => t.specification.version
                },
                {
                    title: "Description",
                    inline: false,
                    render: () => t.specification.description
                },
                {
                    title: "Change since last version",
                    inline: false,
                    render: () => t.specification.changeLog
                },
                {
                    title: "Namespace type",
                    inline: false,
                    render: () => prettierString(t.specification.namespaceType)
                },
                {
                    title: "Changes require approval",
                    inline: false,
                    render: () => t.specification.requireApproval.toString()
                },
                {
                    title: "Metadata should be inherited from ancestor directories",
                    inline: false,
                    render: () => t.specification.inheritable.toString()
                }
            ]}
            entity={t}
            reload={reload}
            updateAclEndpoint={() => ({noop: true})}
            beforeEnd={
                <Section>
                    <Heading.h3>Form preview</Heading.h3>
                    <JsonSchemaForm
                        schema={t.specification.schema}
                        uiSchema={t.specification.uiSchema}
                    />
                </Section>
            }
        />;
    }

    return <MainContainer main={main} />;
};

export default Properties;
