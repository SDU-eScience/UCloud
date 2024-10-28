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

export function scopeCompare(a: CommandScope, b: CommandScope): number {
    return toScopeValue(a) - toScopeValue(b);
}

function toScopeValue(cs: CommandScope): number {
    switch (cs) {
        case CommandScope.ThisPage:
            return 0;
        case CommandScope.GoTo:
            return 1;
        case CommandScope.Application:
            return 2;
        case CommandScope.Job:
            return 3;
        case CommandScope.Drive:
            return 4;
        case CommandScope.File:
            return 5;
        case CommandScope.Link:
            return 6;
        case CommandScope.Project:
            return 7;
        case CommandScope.Accounting:
            return 8;
    }
}


export interface Command {
    icon: CommandIconProvider;
    title: string;
    description: string;
    action: () => void;
    scope: CommandScope;
    actionText?: string;
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
