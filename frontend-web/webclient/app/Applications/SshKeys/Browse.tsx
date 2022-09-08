import * as React from "react";
import {useCallback, useMemo} from "react";
import {ResourceTab, ResourceTabOptions} from "@/Resource/ResourceTabs";
import {StandardList} from "@/ui-components/Browse";
import SshKeyApi from "@/UCloud/SshKeyApi";
import {SidebarPages} from "@/ui-components/Sidebar";

export const SshKeyBrowse: React.FunctionComponent = () => {
    const operations = useMemo(() => SshKeyApi.retrieveOperations(), []);

    const generateCall = useCallback((next?: string) => {
        return SshKeyApi.browse({ next: next, itemsPerPage: 250 });
    }, []);

    return <StandardList
        embedded={false}
        generateCall={generateCall}
        operations={operations}
        renderer={SshKeyApi.renderer}
        title={SshKeyApi.title}
        titlePlural={SshKeyApi.titlePlural}
        header={<ResourceTab active={ResourceTabOptions.SSH_KEYS} />}
        headerSize={48}
        sidebarPage={SidebarPages.Resources}
    />
};

export default SshKeyBrowse;
