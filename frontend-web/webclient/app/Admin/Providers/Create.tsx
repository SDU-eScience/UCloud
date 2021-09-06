import {useInput} from "Admin/LicenseServers";
import {useCloudCommand} from "Authentication/DataHook";
import {Client} from "Authentication/HttpClientInstance";
import MainContainer from "MainContainer/MainContainer";
import {useTitle} from "Navigation/Redux/StatusActions";
import * as React from "react";
import {snackbarStore} from "Snackbar/SnackbarStore";
import * as UCloud from "UCloud";
import {Box, Button, Checkbox, Flex, Input, Label} from "ui-components";
import {errorMessageOrDefault, stopPropagation} from "UtilityFunctions";
import {bulkRequestOf, placeholderProduct} from "DefaultObjects";
import {useHistory} from "react-router";
import {useProjectId} from "Project";

function Create(): JSX.Element | null {
    const [loading, invokeCommand] = useCloudCommand();

    useTitle("Create Provider");

    const projectId = useProjectId();
    const history = useHistory();
    const idInput = useInput();
    const domainInput = useInput();
    const portInput = useInput();
    const [isHttps, setHttps] = React.useState(true);

    if (!Client.userIsAdmin) return null;

    return <MainContainer
        main={<>
            <RS
                title="Providers"
                createRequest={({fields}) =>
                    UCloud.provider.providers.create(bulkRequestOf({
                        id: fields.ID,
                        domain: fields.DOMAIN,
                        https: fields.HTTPS,
                        port: isNaN(fields.PORT) ? undefined : fields.PORT,
                        product: placeholderProduct()
                    }))
                }
                onSubmitSucceded={(res, data) => {
                    if (res) {
                        history.push(`/admin/providers/view/${encodeURI(data.fields.ID)}`);
                    }
                }}
            >
                <RS.Text label="ID" id="ID" placeholder="ID..." required styling={{}} />
                <Flex>
                    <RS.Text id="DOMAIN" label="Domain" placeholder="Domain..." required styling={{width: "80%"}} />
                    <RS.Number id="PORT" label="Port" step="0.1" min={0.0} max={2 ** 16} styling={{ml: "8px", width: "20%"}} />
                </Flex>
                <RS.Checkbox id="HTTPS" label="Uses HTTP" required={false} defaultChecked={false} />
            </RS>
        </>}
    />;

    async function submit() {
        if (projectId == null) {
            snackbarStore.addFailure("Please select a valid project before creating a provider", false);
            return;
        }

        const domain = domainInput.ref.current?.value ?? "";
        const id = idInput.ref.current?.value ?? "";
        const port = parseInt(portInput.ref.current?.value ?? "", 10);

        let error = false;

        if (!domain) {
            error = true;
            domainInput.setHasError(true);
        }

        if (!id) {
            error = true;
            idInput.setHasError(true);
        }

        if (error) return;

        try {
            const res = await invokeCommand(
                UCloud.provider.providers.create(bulkRequestOf({
                    id,
                    domain,
                    https: isHttps,
                    port: isNaN(port) ? undefined : port,
                    product: placeholderProduct()
                })),
                {defaultErrorHandler: false}
            );

            if (res) {
                history.push(`/admin/providers/view/${encodeURI(id)}`);
            }
        } catch (e) {
            snackbarStore.addFailure(errorMessageOrDefault(e, "Failed to create provider"), false);
        }
    }
}

import RS from "Products/CreateProduct";

export default Create;
