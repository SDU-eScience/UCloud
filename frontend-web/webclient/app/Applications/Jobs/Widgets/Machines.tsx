import * as React from "react";
import { useEffect, useMemo, useState } from "react";
import { accounting } from "@/UCloud";
import ComputeProductReference = accounting.ProductReference;
import { Product, productCategoryEquals, ProductCompute } from "@/Accounting";
import * as UCloud from "@/UCloud";
import { ProductSelector } from "@/Products/Selector";

export const reservationMachine = "reservation-machine";

export function findRelevantMachinesForApplication(
    application: UCloud.compute.Application,
    machineSupport: UCloud.compute.JobsRetrieveProductsResponse,
    wallets: UCloud.PageV2<ProductCompute>
): ProductCompute[] {
    return ([] as ProductCompute[]).concat.apply(
        [],
        Object.values(machineSupport.productsByProvider).map(products => {
            return products
                .filter(it => {
                    const tool = application.invocation.tool.tool!;
                    const backend = tool.description.backend;
                    switch (backend) {
                        case "DOCKER":
                            return it.support.docker.enabled;
                        case "SINGULARITY":
                            return false;
                        case "VIRTUAL_MACHINE":
                            return it.support.virtualMachine.enabled &&
                                (tool.description.supportedProviders ?? [])
                                    .some(p => p === it.product.category.provider);
                        case "NATIVE":
                            return it.support.native.enabled &&
                                (tool.description.supportedProviders ?? [])
                                    .some(p => p === it.product.category.provider);
                    }
                })
                .filter(product =>
                    wallets.items.some(wallet => productCategoryEquals(product.product.category, wallet.category))
                )
                .map(it => it.product);
        })
    );
}

export const Machines: React.FunctionComponent<{
    machines: ProductCompute[];
    onMachineChange?: (product: ProductCompute) => void;
}> = props => {
    const [selected, setSelectedOnlyByListener] = useState<ProductCompute | null>(null);
    const filteredMachines = useMemo(() => {
        return props.machines.filter(it => it.name !== "syncthing");
    }, [props.machines]);

    useEffect(() => {
        let listener: (() => void) | null = null;
        const valueInput = document.getElementById(reservationMachine) as HTMLInputElement | null;
        if (valueInput) {
            listener = () => {
                const value = valueInput.value;
                if (value === "") {
                    setSelectedOnlyByListener(null);
                } else {
                    const ref = JSON.parse(value) as ComputeProductReference;
                    const newMachine = props.machines.find(it =>
                        it.name === ref.id &&
                        it.category.name === ref.category &&
                        it.category.provider === ref.provider
                    );

                    if (newMachine) {
                        setSelectedOnlyByListener(newMachine);
                        if (props.onMachineChange) props.onMachineChange(newMachine);
                    }
                }
            };

            listener();
            valueInput.addEventListener("change", listener);
        }
        return () => {
            if (valueInput && listener) valueInput.removeEventListener("change", listener);
        };
    }, [props.machines, props.onMachineChange]);

    return (
        <>
            <input type="hidden" id={reservationMachine} />
            <ProductSelector
                type={"COMPUTE"}
                products={filteredMachines}
                selected={selected}
                onSelect={setMachineReservation}
            />
        </>
    )
};


export function setMachineReservation(compute: Product | null): void {
    const valueInput = document.getElementById(reservationMachine) as HTMLInputElement | null;
    if (valueInput === null) throw "Component is no longer mounted but setSelected was called";
    if (compute === null) {
        valueInput.value = "";
        valueInput.dispatchEvent(new Event("change"));
    } else {
        if (compute.productType !== "COMPUTE") return;
        setMachineReservationFromRef({
            provider: compute.category.provider,
            category: compute.category.name,
            id: compute.name
        });
    }
}

export function setMachineReservationFromRef(ref: ComputeProductReference): void {
    const valueInput = document.getElementById(reservationMachine) as HTMLInputElement | null;
    if (valueInput === null) throw "Component is no longer mounted but setSelected was called";

    valueInput.value = JSON.stringify(ref);
    valueInput.dispatchEvent(new Event("change"));
}

export function validateMachineReservation(): ComputeProductReference | null {
    const valueInput = document.getElementById(reservationMachine) as HTMLInputElement | null;
    if (valueInput === null) throw "Component is no longer mounted but validateMachineReservation was called";

    if (valueInput.value === "") return null;
    return JSON.parse(valueInput.value) as ComputeProductReference
}
