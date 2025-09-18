import {IconName} from "@/ui-components/Icon";
import {ExternalStoreBase} from "@/Utilities/ReduxUtilities";
import {useEffect, useSyncExternalStore} from "react";
import {fuzzySearch} from "@/Utilities/CollectionUtilities";
import {doNothing} from "@/UtilityFunctions";
import {ThemeColor} from "@/ui-components/theme";

export interface CommandIconProviderSimple {
    type: "simple";
    icon: IconName;
    color?: ThemeColor;
    color2?: ThemeColor;
}

export interface CommandIconProviderImage {
    type: "image";
    imageUrl: string;
}

export type CommandIconProvider =
    | CommandIconProviderSimple
    | CommandIconProviderImage
    ;


export enum CommandScope {
    ThisPage = "On this page",
    GoTo = "Shortcut",
    Application = "Applications",
    Job = "Jobs",
    Drive = "Drives",
    File = "Files",
    Link = "Links",
    Project = "Projects",
    Accounting = "Allocations",
}

export interface Command {
    icon: CommandIconProvider;
    title: string;
    description: string;
    action: () => void;
    scope: CommandScope;
    actionText?: string;
    defaultHidden?: boolean;
}

export type CommandProvider = (query: string, emit: (cmd: Command) => void) => {onCancel: () => void};

export function staticProvider(commands: Command[]): CommandProvider {
    return (query, emit) => {
        const filteredCommands = query.length > 2 ? commands : commands.filter(it => !it.defaultHidden);
        const results = fuzzySearch(filteredCommands, ["title", "description"], query, {sort: true});
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
