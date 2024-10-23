import {IconName} from "@/ui-components/Icon";
import {ExternalStoreBase} from "@/Utilities/ReduxUtilities";
import {useEffect, useSyncExternalStore} from "react";
import {fuzzySearch} from "@/Utilities/CollectionUtilities";
import {doNothing} from "@/UtilityFunctions";

export interface CommandIconProviderSimple {
    type: "simple";
    icon: IconName;
}

export interface CommandIconProviderDom {
    type: "dom";
    dom: (size: number) => HTMLElement;
}

export interface CommandIconProviderImage {
    type: "image";
    imageUrl: string;
}

export type CommandIconProvider =
    | CommandIconProviderSimple
    | CommandIconProviderDom
    | CommandIconProviderImage
    ;


export enum CommandScope {
    ThisPage,
    GoTo,
    Application,
    Job,
    Drive,
    File,
    Link,
    Project,
    Accounting,
};

export function scopePriority(scope: CommandScope): number {
    // lower is higher priority
    return scope;
}

export interface Command {
    icon: CommandIconProvider;
    title: string;
    description: string;
    action: () => void;
    scope: CommandScope;
}

export type CommandProvider = (query: string, emit: (cmd: Command) => void) => {onCancel: () => void};

export function staticProvider(commands: Command[]): CommandProvider {
    return (query, emit) => {
        const results = fuzzySearch(commands, ["title", "description"], query, {sort: true});
        for (const result of results) {
            emit(result);
        }

        return {onCancel: doNothing};
    };
}

export const commandProviderStore = new class extends ExternalStoreBase {
    private providers: CommandProvider[] = [];

    registerProvider(provider: CommandProvider) {
        this.providers = [...this.providers, provider];
        this.emitChange();
    }

    unregisterProvider(provider: CommandProvider) {
        this.providers = this.providers.filter(it => it !== provider);
        this.emitChange();
    }

    getSnapshot(): CommandProvider[] {
        return this.providers;
    }
};

export function useCommandProviderList(): CommandProvider[] {
    return useSyncExternalStore(s => commandProviderStore.subscribe(s), () => commandProviderStore.getSnapshot());
}

export function useProvideCommands(provider: CommandProvider) {
    useEffect(() => {
        commandProviderStore.registerProvider(provider);
        return () => {
            commandProviderStore.unregisterProvider(provider);
        };
    }, [provider]);
}

export {CommandPalette} from "./CommandPalette";
