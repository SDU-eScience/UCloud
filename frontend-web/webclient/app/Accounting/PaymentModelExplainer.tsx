import * as React from "react";
import {PaymentModel} from "Accounting/index";
import {creditFormatter} from "Project/Resources";

export const PaymentModelExplainer: React.FunctionComponent<{
    model: PaymentModel;
    price: number;
}> = ({model, price}) => {
    if (price === 0 || model === "FREE_BUT_REQUIRE_BALANCE") {
        return <>Free</>;
    }

    switch (model) {
        case "PER_ACTIVATION":
            return <>Per activation: {creditFormatter(price)}</>;
    }
}
