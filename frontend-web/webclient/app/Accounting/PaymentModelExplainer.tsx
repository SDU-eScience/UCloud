import * as React from "react";
import {PaymentModel} from "Accounting/index";

export const PaymentModelExplainer: React.FunctionComponent<{model: PaymentModel}> = ({model}) => {
    switch (model) {
        case "PER_ACTIVATION":
            return <>Per activation</>;
    }
}
