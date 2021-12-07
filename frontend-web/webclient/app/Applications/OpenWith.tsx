import * as React from "react";
import {UFile} from "@/UCloud/FilesApi";
import {apiUpdate, useCloudAPI, useCloudCommand} from "@/Authentication/DataHook";
import {accounting, BulkResponse, compute, FindByStringId, PaginationRequestV2} from "@/UCloud";
import ApplicationWithExtension = compute.ApplicationWithExtension;
import {useCallback, useEffect, useMemo, useState} from "react";
import {ItemRenderer, StandardCallbacks, StandardList} from "@/ui-components/Browse";
import {AppToolLogo} from "@/Applications/AppToolLogo";
import {Operation} from "@/ui-components/Operation";
import {ProductSelector} from "@/Resource/ProductSelector";
import {FileCollection} from "@/UCloud/FileCollectionsApi";
import JobsApi from "@/UCloud/JobsApi";
import {SupportByProvider} from "@/UCloud/ResourceApi";
import {Button} from "@/ui-components";
import {bulkRequestOf} from "@/DefaultObjects";
import {getParentPath} from "@/Utilities/FileUtilities";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import {useHistory} from "react-router";
import {Product} from "@/Accounting";
import {dialogStore} from "@/Dialog/DialogStore";

function findApplicationsByExtension(
    request: { files: string[] } & PaginationRequestV2
): APICallParameters<{ files: string[] } & PaginationRequestV2> {
    return apiUpdate(request, "/api/hpc/apps", "bySupportedFileExtension");
}

const appRenderer: ItemRenderer<ApplicationWithExtension> = {
    Icon: props =>
        <AppToolLogo name={props.resource?.metadata.name ?? "app"} type={"APPLICATION"} size={props.size}/>,
    MainTitle: props => !props.resource ? null : <>{props.resource.metadata.title}</>,
};

const operations: Operation<ApplicationWithExtension, StandardCallbacks<ApplicationWithExtension> & ExtraCallbacks>[] = [
    {
        text: "Launch",
        icon: "play",
        primary: true,
        enabled: selected => selected.length === 1,
        onClick: (selected, cb) => {
            cb.setSelectedApplication(selected[0]);
        }
    }
];

interface ExtraCallbacks {
    setSelectedApplication: (app: ApplicationWithExtension) => void;
}

interface OpenWithProps {
    file: UFile;
    collection: FileCollection;
}

export const OpenWith: React.FunctionComponent<OpenWithProps> = ({file, collection}) => {
    const [productsWithSupport, fetchProducts] = useCloudAPI<SupportByProvider>({noop: true}, {productsByProvider: {}});
    const [selectedProduct, setSelectedProduct] = useState<Product | null>(null);
    const [selectedApplication, setSelectedApplication] = useState<ApplicationWithExtension | null>(null);
    const [commandLoading, invokeCommand] = useCloudCommand();
    const history = useHistory();
    useEffect(() => {
        fetchProducts(JobsApi.retrieveProducts());
    }, []);

    const products = useMemo(() => {
        const relevantProducts = productsWithSupport.data.productsByProvider[collection.specification.product.provider];
        if (relevantProducts) {
            return relevantProducts.map(it => it.product as unknown as Product)
        } else {
            return [];
        }
    }, [productsWithSupport.data, collection]);

    const generateCall = useCallback(next => {
        const normalizedFileId = file.status.type === "DIRECTORY" ? `${file.id}/` : file.id;

        return findApplicationsByExtension({
            files: [normalizedFileId],
            itemsPerPage: 50,
            next: next
        });
    }, [file.id]);

    const callbacks: ExtraCallbacks = useMemo(() => ({
        setSelectedApplication
    }), [setSelectedApplication]);

    const onProductSelected = useCallback((product) => {
        setSelectedProduct(product);
    }, []);

    const launch = useCallback(async () => {
        if (!selectedProduct || !selectedApplication) return;
        try {
            const response = await invokeCommand<BulkResponse<FindByStringId | null>>(
                JobsApi.create(bulkRequestOf({
                    application: {
                        name: selectedApplication.metadata.name,
                        version: selectedApplication.metadata.version,
                    },
                    product: {
                        id: selectedProduct.name,
                        provider: selectedProduct.category.provider,
                        category: selectedProduct.category.name
                    },
                    parameters: {},
                    replicas: 1,
                    allowDuplicateJob: true,
                    timeAllocation: {
                        hours: 3,
                        minutes: 0,
                        seconds: 0
                    },
                    name: undefined,
                    resources: [{
                        type: "file",
                        path: file.status.type === "DIRECTORY" ? file.id : getParentPath(file.id),
                        readOnly: false
                    }],
                    openedFile: file.id
                })),
                {defaultErrorHandler: false}
            );

            dialogStore.success();

            const ids = response?.responses;
            if (!ids || ids.length === 0) {
                snackbarStore.addFailure("UCloud failed to submit the job", false);
                return;
            }

            history.push(`/jobs/properties/${ids[0]?.id}?app=${selectedApplication.metadata.name}`);
        } catch (e) {
            snackbarStore.addFailure("UCloud failed to submit the job", false);
        }
    }, [selectedProduct, selectedApplication, file]);

    return <>
        <StandardList generateCall={generateCall} renderer={appRenderer} operations={operations}
            title={"Application"} embedded={"dialog"} extraCallbacks={callbacks}
            hide={selectedApplication != null}
            emptyPage={<>Found no suitable applications for this file type. You can explore more applications
                by clicking on Apps in the sidebar.</>}
        />

        {!selectedApplication ? null : <>
            <ProductSelector products={products} onProductSelected={onProductSelected} />
            <Button mt={"8px"} fullWidth onClick={launch} disabled={commandLoading}>Launch</Button>
        </>}
    </>;
};
