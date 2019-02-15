import PromiseKeeper from "PromiseKeeper";

describe("Promise Keeper", () => {
    test("New Promise Keeper has no promises", () =>
        expect((new PromiseKeeper() as any).promises.length).toBe(0)
    );

    test("Cancel all works with no promises", () => {
        const keeper = new PromiseKeeper();
        keeper.cancelPromises();
        expect((keeper as any).promises.length).toBe(0);
    });

    test("Make promise and add to Promise Keeper", () => {
        const keeper = new PromiseKeeper();
        keeper.makeCancelable<number>(new Promise(resolve => resolve(1)));
        expect((keeper as any).promises.length).toBe(1);
    });

    test("Make resolving promise and add to Promise Keeper", () => {
        const keeper = new PromiseKeeper();
        keeper.makeCancelable<number>(new Promise(resolve => resolve(1)));
        keeper.cancelPromises();
        expect((keeper as any).promises.length).toBe(0);
    });

    test("Make rejecting promise and add to Promise Keeper", () => {
        const keeper = new PromiseKeeper();
        keeper.makeCancelable<number>(new Promise((_, reject) => reject(1)));
        keeper.cancelPromises();
        expect((keeper as any).promises.length).toBe(0);
    });

    test("Make resolving promise and cancel before resolved", () => {
        const keeper = new PromiseKeeper();
        keeper.makeCancelable<number>(new Promise(resolve => { keeper.cancelPromises(); resolve(1) }));
        keeper.cancelPromises();
        expect((keeper as any).promises.length).toBe(0);
    });

    test("Make rejecting promise and cancel before rejected", () => {
        const keeper = new PromiseKeeper();
        keeper.makeCancelable<number>(new Promise((_, reject) => { keeper.cancelPromises(); reject(1) }));
        keeper.cancelPromises();
        expect((keeper as any).promises.length).toBe(0);
    });
})