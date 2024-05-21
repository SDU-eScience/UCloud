import * as React from "react";
import {useCloudCommand, useCloudAPI} from "@/Authentication/DataHook";
import {availableGifts, AvailableGiftsResponse, claimGift} from "@/Services/Gifts/index";
import {useEffect} from "react";

export const AutomaticGiftClaim: React.FunctionComponent = () => {
    const [gifts] = useCloudAPI<AvailableGiftsResponse>(
        availableGifts({}),
        {gifts: []}
    );

    const [, runWork] = useCloudCommand();

    useEffect(() => {
        for (const gift of gifts.data.gifts) {
            runWork(claimGift({giftId: gift.id}));
        }
    }, [gifts.data.gifts]);
    return null;
};
