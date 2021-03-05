import {useRouteMatch} from "react-router";
import * as React from "react";
import {useCloudAPI} from "Authentication/DataHook";
import * as UCloud from "UCloud";
import LoadingSpinner from "LoadingIcon/LoadingIcon";
import MainContainer from "MainContainer/MainContainer";
import {ResourcePage} from "ui-components/ResourcePage";
import {useCallback} from "react";
import {TextArea} from "ui-components";
import {doNothing} from "UtilityFunctions";

function View(): JSX.Element | null {
    const match = useRouteMatch<{ id: string }>();
    const {id} = match.params;
    const [provider, fetchProvider] = useCloudAPI<UCloud.provider.Provider | null>(
        UCloud.provider.providers.retrieve({id}),
        null
    );

    const reload = useCallback(() => {
        fetchProvider(UCloud.provider.providers.retrieve({id}));
    }, [id]);

    if (provider.loading && provider.data == null) return <MainContainer main={<LoadingSpinner/>}/>;
    if (provider.data == null) return null;

    return (
        <MainContainer
            main={
                <ResourcePage
                    entityName={"Provider"}
                    aclOptions={[{icon: "edit", name: "EDIT", title: "Edit"}]}
                    entity={provider.data}
                    reload={reload}
                    showMissingPermissionHelp={false}
                    stats={[
                        {
                            title: "Domain",
                            render: t => {
                                let stringBuilder = "";
                                if (t.specification.https) {
                                    stringBuilder += "https://"
                                } else {
                                    stringBuilder += "http://"
                                }

                                stringBuilder += t.specification.domain;

                                if (t.specification.port) {
                                    stringBuilder += ":";
                                    stringBuilder += t.specification.port;
                                }

                                return stringBuilder;
                            }
                        },
                        {
                            title: "Refresh Token",
                            // eslint-disable-next-line react/display-name
                            render: t => <TextArea width="100%" value={t.refreshToken} rows={3} onChange={doNothing}/>,
                            inline: false,
                        },
                        {
                            title: "Certificate",
                            // eslint-disable-next-line react/display-name
                            render: t => <TextArea width="100%" value={t.publicKey} rows={10} onChange={doNothing}/>,
                            inline: false,
                        },
                    ]}
                    updateAclEndpoint={UCloud.provider.providers.updateAcl}
                />
            }
        />
    );
}

export default View;
