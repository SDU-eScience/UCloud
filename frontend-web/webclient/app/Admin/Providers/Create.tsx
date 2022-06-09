import {Client} from "@/Authentication/HttpClientInstance";
import MainContainer from "@/MainContainer/MainContainer";
import * as React from "react";
import * as UCloud from "@/UCloud";
import {Flex} from "@/ui-components";
import {bulkRequestOf, placeholderProduct} from "@/DefaultObjects";
import {useHistory} from "react-router";
import RS from "@/Products/CreateProduct";
import {useTitle} from "@/Navigation/Redux/StatusActions";

function Create(): JSX.Element | null {
    const history = useHistory();
    useTitle("Create Provider");

    if (!Client.userIsAdmin) return null;

    return <MainContainer
        main={<>
            <RS
                title="Providers"
                submitText="Create provider"
                createRequest={async ({fields}) =>
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
                        history.push(`/providers`);
                    }
                }}
            >
                <RS.Text label="ID" id="ID" placeholder="ID..." required styling={{}} />
                <Flex>
                    <RS.Text id="DOMAIN" label="Domain" placeholder="Domain..." required styling={{width: "80%"}} />
                    <RS.Number id="PORT" label="Port" step="0.1" min={0.0} max={2 ** 16} styling={{ml: "8px", width: "20%"}} />
                </Flex>
                <RS.Checkbox id="HTTPS" label="Uses HTTPS" required={false} defaultChecked={false} styling={{}} />
            </RS>
        </>}
    />;
}

export default Create;
