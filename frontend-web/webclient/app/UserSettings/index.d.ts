import PromiseKeeper from "PromiseKeeper";

export interface UserSettingsState {
    promiseKeeper: PromiseKeeper
    currentPassword: string
    newPassword: string
    repeatedPassword: string
    error: boolean
    repeatPasswordError: boolean
}

export type UserSettingsFields = keyof UserSettingsState

export interface TwoFactorSetupState {
    challengeId?: string
    qrCode?: string
    verificationCode: string
    isConnectedToAccount?: boolean
    isLoading: boolean
}