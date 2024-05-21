import * as React from "react";
import {createRoot} from "react-dom/client";
import {useLayoutEffect, useRef} from "react";

export class ReactStaticRenderer {
    private fragment = document.createDocumentFragment();
    private readonly _promise: Promise<this>;
    public get promise(): Promise<this> {
        return this._promise;
    }

    constructor(node: () => React.ReactElement) {
        const fragment = document.createDocumentFragment();
        const root = createRoot(fragment);

        this._promise = new Promise<this>((resolve, reject) => {
            const Component: React.FunctionComponent<{children: React.ReactNode}> = props => {
                const div = useRef<HTMLDivElement | null>(null);
                useLayoutEffect(() => {
                    resolve(this);
                }, []);

                return <div ref={div}>{props.children}</div>;
            };

            root.render(<Component>{node()}</Component>);
        });

        this._promise.finally(() => {
            this.fragment.append(fragment.cloneNode(true));
            root.unmount();
        });
    }

    clone(): DocumentFragment {
        return this.fragment.cloneNode(true) as DocumentFragment;
    }
}
