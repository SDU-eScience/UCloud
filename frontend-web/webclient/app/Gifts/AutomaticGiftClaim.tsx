import * as React from "react";
import {useAsyncCommand, useCloudAPI} from "Authentication/DataHook";
import {availableGifts, AvailableGiftsResponse, claimGift} from "Gifts/index";
import {useEffect} from "react";

export const AutomaticGiftClaim: React.FunctionComponent = () => {
    const [gifts] = useCloudAPI<AvailableGiftsResponse>(
        availableGifts({}),
        {gifts: []}
    );

    const [, runWork] = useAsyncCommand();

    useEffect(() => {
        for (const gift of gifts.data.gifts) {
            runWork(claimGift({giftId: gift.id}));
        }
    }, [gifts.data.gifts]);
    return null;
};
