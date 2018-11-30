import Cloud from "Authentication/lib";

const sendSupportMessage = (message: string, cloud: Cloud): Promise<any> => 
    cloud.post(`/support/ticket`, { message })