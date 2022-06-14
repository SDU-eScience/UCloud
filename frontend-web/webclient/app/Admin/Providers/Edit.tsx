import {Client} from "@/Authentication/HttpClientInstance";
import MainContainer from "@/MainContainer/MainContainer";
import * as React from "react";
import * as UCloud from "@/UCloud";
import {Flex} from "@/ui-components";
import Loading from "@/LoadingIcon/LoadingIcon";
import {bulkRequestOf, placeholderProduct} from "@/DefaultObjects";
import {useHistory, useParams} from "react-router";
import RS from "@/Products/CreateProduct";
import {useTitle} from "@/Navigation/Redux/StatusActions";
import {buildQueryString} from "@/Utilities/URIUtilities";
import ProvidersApi, {Provider} from "@/UCloud/ProvidersApi";
import {useCloudAPI} from "@/Authentication/DataHook";
import {render} from "react-dom";

function getByIdRequest(payload: {id: string}): APICallParameters<{id: string}> {
    return {
        path: buildQueryString("/providers/retrieve", payload),
        payload
    };
}

function Edit(): JSX.Element | null {
    const {id} = useParams<{id: string}>();
    const [provider, setParams, params] = useCloudAPI<Provider | null, {id: string}>(getByIdRequest({id}), null);
    const history = useHistory();

    React.useEffect(() => {
        setParams(getByIdRequest({id}));
    }, [id]);

    useTitle("Edit Provider");

    if (provider.loading) return <MainContainer headerSize={0} main={<Loading size={24} />} />;

    if (!Client.userIsAdmin) return null;

    return <MainContainer
        main={<>
            <RS
                title="Providers"
                submitText="Save changes"
                createRequest={async ({fields}) => {
                    return ProvidersApi.update({
                        id: id,
                        specification: {
                            id: provider.data?.specification.id ?? fields.ID,
                            domain: fields.DOMAIN,
                            https: fields.HTTPS,
                            port: isNaN(fields.PORT) ? undefined : fields.PORT,
                            product: placeholderProduct()
                        }
                    })
                }}
                onSubmitSucceded={(res, data) => {
                    if (res) {
                        history.push(`/providers`);
                    }
                }}
            >
                <RS.Text
                    label="ID"
                    id="ID"
                    placeholder="ID..."
                    required
                    disabled
                    styling={{}}
                    defaultValue={provider.data?.specification.id}
                />
                <Flex>
                    <RS.Text
                        id="DOMAIN"
                        label="Domain"
                        placeholder="Domain..."
                        required
                        styling={{width: "80%"}}
                        defaultValue={provider.data?.specification.domain}
                    />
                    <RS.Number
                        id="PORT"
                        label="Port"
                        step="0.1"
                        min={0.0}
                        max={2 ** 16}
                        styling={{ml: "8px", width: "20%"}}
                        defaultValue={provider.data?.specification.port ?? ""}
                    />
                </Flex>
                <RS.Checkbox
                    id="HTTPS"
                    label="Uses HTTPS"
                    required={false}
                    defaultChecked={provider.data?.specification.https ?? false}
                    styling={{}}
                />
            </RS>
        </>}
    />;
}

export default Edit;
