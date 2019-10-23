export interface TwoFactorSetupState {
    challengeId?: string;
    qrCode?: string;
    verificationCode: string;
    isConnectedToAccount?: boolean;
}
