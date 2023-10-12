import {Client} from "@/Authentication/HttpClientInstance";
import MainContainer from "@/MainContainer/MainContainer";
import * as React from "react";
import * as UCloud from "@/UCloud";
import {Flex} from "@/ui-components";
import Loading from "@/LoadingIcon/LoadingIcon";
import {bulkRequestOf, placeholderProduct} from "@/DefaultObjects";
import {useNavigate, useParams} from "react-router";
import RS from "@/Products/CreateProduct";
import {useTitle} from "@/Navigation/Redux/StatusActions";
import {buildQueryString} from "@/Utilities/URIUtilities";
import ProvidersApi, {Provider} from "@/UCloud/ProvidersApi";
import {useCloudAPI} from "@/Authentication/DataHook";
import {snackbarStore} from "@/Snackbar/SnackbarStore";

function getByIdRequest(payload: { id: string }): APICallParameters<{ id: string }> {
    return {
        path: buildQueryString("/providers/retrieve", payload),
        payload
    };
}

function Save(): JSX.Element | null {
    const {id} = useParams<{ id: string }>();
    const [provider, setParams] = useCloudAPI<Provider | null, { id: string }>({noop: true}, null);
    const navigate = useNavigate();

    React.useEffect(() => {
        if (id) {
            setParams(getByIdRequest({id}));
        }
    }, [id]);

    const title = provider.data ? "Edit Provider" : "Create Provider";

    useTitle(title);

    if (provider.loading) return <MainContainer headerSize={0} main={<Loading size={24}/>}/>;

    if (!Client.userIsAdmin) return null;

    return <MainContainer
        main={<>
            <RS
                title="Providers"
                submitText={provider.data ? "Save changes" : "Create"}
                createRequest={async ({fields}) => {
                    if (provider.data) {
                        return ProvidersApi.update(bulkRequestOf({
                            id: provider.data?.specification.id ?? fields.ID,
                            domain: fields.DOMAIN,
                            https: fields.HTTPS,
                            port: isNaN(fields.PORT) ? undefined : fields.PORT,
                            product: placeholderProduct()
                        }));
                    } else {
                        return UCloud.provider.providers.create(bulkRequestOf({
                            id: fields.ID,
                            domain: fields.DOMAIN,
                            https: fields.HTTPS,
                            port: isNaN(fields.PORT) ? undefined : fields.PORT,
                            product: placeholderProduct()
                        }));
                    }
                }}
                onSubmitSucceded={(res, data) => {
                    if (res) {
                        navigate(`/providers`);
                    }
                }}
                onSubmitError={(error) => {
                    snackbarStore.addFailure(error, false);
                }}
            >
                <RS.Text
                    label="ID"
                    id="ID"
                    placeholder="ID..."
                    required
                    disabled={provider.data ? true : false}
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
                        step="1"
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

export default Save;
