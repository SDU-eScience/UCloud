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
    if (creator.isUnavailable()) return null;

    return <MainContainer
        main={<>
            <Box maxWidth={800} mt={30} marginLeft="auto" marginRight="auto">
                <Label>
                    ID
                    <Input error={idInput.hasError} ref={idInput.ref} placeholder="ID..." />
                </Label>
                <Flex>
                    <Label width="80%">
                        Domain
                        <Input error={domainInput.hasError} ref={domainInput.ref} placeholder="Domain..." />
                    </Label>
                    <Label ml="8px" width="20%">
                        Port
                        <Input type="number" min={0} max={65536} ref={portInput.ref} placeholder="Port..." />
                    </Label>
                </Flex>
                <Label>
                    <Checkbox onClick={() => setHttps(h => !h)} checked={isHttps} onChange={stopPropagation} />
                    Uses HTTPS
                </Label>

                <Button my="12px" disabled={loading} onClick={submit}>Submit</Button>
            </Box>
            <div>DIVIDER</div>
            <React.Fragment key="creation">{creator.render()}</React.Fragment>
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

import ProductCreator, {FieldCreator} from "Products/CreateProduct";

class ProviderCreator extends ProductCreator<any> {
    title = "Provider";
    isUnavailable = () => !Client.userIsAdmin;
    fields = [
        FieldCreator.makeTextField("ID...", "ID", true),
        FieldCreator.makeGroup([
            FieldCreator.makeTextField("Domain...", "Domain", true, {width: "80%"}),
            FieldCreator.makeNumberField("Port...", "Port", true, 0, 2 ** 16, {ml: "8px", width: "20%"})
        ]),
        FieldCreator.makeCheckboxField("Uses HTTP", false)
    ];

    public createRequest() {
        const fields = this.extractFieldsByLabel(this.fields);
        return UCloud.provider.providers.create(bulkRequestOf({
            id: fields["ID"] as string,
            domain: fields["Domain"] as string,
            https: fields["Uses HTTP"] as boolean,
            port: isNaN(fields["Port"] as number) ? undefined : fields["Port"] as number,
            product: placeholderProduct()
        }));
    }
}

const creator = new ProviderCreator();

export default Create;
