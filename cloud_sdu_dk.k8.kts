config("*") { ctx -> 
    Configuration.configure("domain", when (ctx.environment) {
        "test", "development" -> "dev.cloud.sdu.dk"
        else -> "cloud.sdu.dk"
    })

    Configuration.configure("defaultScale", when (ctx.environment) {
        "test", "development" -> 1
        else -> 3
    })
}

config("ceph") { ctx ->
    configure("enabled", true)

    if (ctx.environment in setOf("test", "development")) {
        configure("monitors", "10.135.0.15:6789,10.135.0.16:6789,10.135.0.17:6789")
    } else {
        configure("monitors", "172.26.3.1:6789,172.26.3.2:6789,172.26.3.3:6789")
    }
}

config("stolon") {
    configure("claimStorageClass", "rbd")
}

config("redis") { ctx ->
    configure("claimStorageClass", "rbd")

    if (ctx.environment == "production") {
        configure("claimSize", "5000Gi")

        configure("slaveCpu", "4000m")
        configure("slaveMem", "4096Mi")

        configure("masterCpu", "8000m")
        configure("masterMem", "8192Mi")
    } else {
        configure("claimSize", "1000Gi")
    }
}

config("kibana") { ctx ->
    when (ctx.environment) {
        "production" -> {
            configure("hostname", "elasticsearch-newmaster")
        }

        "test" -> {
            configure("hostname", "elasticsearch-master")
        }

        "development" -> {
            configure("hostname", "elasticsearch-newmaster")
        }
    }
}

config("elasticsearch") { ctx ->
    configure("masterCpu", "2000m")
    configure("clientCpu", "2000m")
    configure("dataCpu", "8000m")

    configure("masterMem", "12Gi")
    configure("dataMem", "30Gi")
    configure("clientMem", "12Gi")

    configure("storageClassName", "rbd")

    when (ctx.environment) {
        "production" -> {
            configure("masterCount", 3)
            configure("clientCount", 2)
            configure("dataCount", 4)
            configure("dataStorage", "5000Gi")
        }

        "test" -> {
            configure("masterCount", 2)
            configure("clientCount", 2)
            configure("dataCount", 2)
            configure("minMasterNodes", 0)
            configure("dataStorage", "500Gi")
        }

        "development" -> {
            configure("masterCount", 3)
            configure("clientCount", 2)
            configure("dataCount", 3)
            configure("dataStorage", "500Gi")
        }
    }
}

config("app-orchestrator") { ctx ->
    when (ctx.environment) {
        "development", "test" -> {
            configure("domain", "dev.cloud.sdu.dk")
        }

        "production" -> {
            configure("domain", "cloud.sdu.dk")
        }
    }
}

config("project") { ctx ->
    configure("enabled", ctx.environment != "production")
}

config("webdav") { ctx ->
    when (ctx.environment) {
        "test" -> {
            configure("domain", "davs.dev.cloud.sdu.dk")
        }

        "development" -> {
            configure("domain", "webdav.dev.cloud.sdu.dk")
        }

        "production" -> {
            configure("domain", "dav.cloud.sdu.dk")
        }
    }
}

config("storage") { ctx ->
    when (ctx.environment) {
        "development" -> {
            configure("mountLocation", "dev")
        }

        "test" -> {
            configure("mountLocation", "test")
        }
    }
}

config("app-kubernetes") { ctx ->
    when (ctx.environment) {
        "test" -> {
            configure("prefix", "apps-")
            configure("domain", "dev.cloud.sdu.dk")
            configure("networkInterface", "eno49")
        }

        "development" -> {
            configure("prefix", "app-")
            configure("domain", "dev.cloud.sdu.dk")
            configure("networkInterface", "eno49")
        }

        "production" -> {
            configure("prefix", "app-")
            configure("domain", "cloud.sdu.dk")
            configure("networkInterface", "bond0.20")
        }
    }

    configure("internalEgressWhitelist", listOf(
        // allow tek-ansys.tek.c.sdu.dk
        "10.144.4.166/32",

        // allow tek-comsol0a.tek.c.sdu.dk
        "10.144.4.169/32",

        // coumputational biology server SDU (requested by Emiliano)
        "10.137.1.93/32",

        // Ansys
        "172.16.0.101/32",
        // COMSOL
        "172.16.0.102/32"
    ))
}

config("audit-ingestion") { ctx ->
    when (ctx.environment) {
        "development", "production" -> {
            configure("secret", "elasticsearch-logging-cluster-credentials")
        }
    }
}

config("auth") { ctx ->
    configure("scheme", "https")

    when (ctx.environment) {
        "development" -> {
            configure("trustLocalhost", true)
            configure("host", "dev.cloud.sdu.dk")
            configure("cert", """
                -----BEGIN CERTIFICATE-----
                MIIDfzCCAmegAwIBAgIUDlxDPskNFRztsjog68XuA4jjGZkwDQYJKoZIhvcNAQEL
                BQAwTzELMAkGA1UEBhMCREsxDDAKBgNVBAgMA0Z5bjEPMA0GA1UEBwwGT2RlbnNl
                MREwDwYDVQQKDAhlU2NpZW5jZTEOMAwGA1UEAwwFQnJpYW4wHhcNMTkxMDAxMTEz
                NTM0WhcNMjAwOTMwMTEzNTM0WjBPMQswCQYDVQQGEwJESzEMMAoGA1UECAwDRnlu
                MQ8wDQYDVQQHDAZPZGVuc2UxETAPBgNVBAoMCGVTY2llbmNlMQ4wDAYDVQQDDAVC
                cmlhbjCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAMBg0lJG0fpH02pc
                46DEfBm4xe0caQpptxQX4uq8Bl4jW8Lf+ycl2UC3qP/ccCsBmAvmSS1DWRnxSDjc
                eWvSP7oYJarVhPlavj/uNpRDCIs0JBGIRvJB2szriJCwxJXtAyAqTOi9apUQq8we
                9gXB2E48HLo230xnOfUR1++O01aQOVosIrNZvZEwxP6HHXL6TYVRRzlfi0OgYjMs
                by5Jx65l2HVqJZGV/WOfwBYVdaJEJGM3PMXIuZSJmRJX/clgrjCeaQRFMt/BDPnF
                sjfg2xuTZz8dhDpsYel2d9GdDpmI5Yb7bfXaj2AYZ+KXcGIuhNPV8dycvSFgqH4B
                btTrFwUCAwEAAaNTMFEwHQYDVR0OBBYEFJpREMDgQ+CYNKWKE955VW5GtE82MB8G
                A1UdIwQYMBaAFJpREMDgQ+CYNKWKE955VW5GtE82MA8GA1UdEwEB/wQFMAMBAf8w
                DQYJKoZIhvcNAQELBQADggEBAKKhwgVtqPxoAmaKjC/i4KWpYltCBZtQB0NwXRxp
                WlFZ/rnPxA8dCDej1T/dvW3LgCF2su91e44ImH/z+6liJa6O5yHxs/rWT5RsdDNy
                gFMmOBcCHgCS1bcHyz0ZUtOkPvFLODC2vfdxKa3fks7C5O2CKDsBkIMxqu/TMU1S
                /DY5UHyr0nI2jur2M/xcYTEg4RuQRljr4i9vBENdZd/wfKAEPgRDjDVMoxhdi4R6
                zCEdr3vdt8PNI7AbaO7N4znbT8ftmhtxs8+YlgmomSI4vu8FvkDk34xx4T0A6OCC
                dy5pKr7I/0JbWjqjdb26wgDPhAL8Ts6wV6o23xNtAGgJhJ0=
                -----END CERTIFICATE-----
            """.trimIndent())
        }

        "test" -> {
            configure("trustLocalhost", true)
            configure("host", "staging.dev.cloud.sdu.dk")
            configure("cert", """
                -----BEGIN CERTIFICATE-----
                MIIDSDCCAjACCQCOMA9vihmVcDANBgkqhkiG9w0BAQsFADBmMQswCQYDVQQGEwJE
                SzETMBEGA1UECAwKU3lkZGFubWFyazELMAkGA1UEBwwCTkExETAPBgNVBAoMCGVT
                Y2llbmNlMREwDwYDVQQLDAhlU2NpZW5jZTEPMA0GA1UEAwwGdWNsb3VkMB4XDTIw
                MDIxODEzNDYyNloXDTIxMDIxNzEzNDYyNlowZjELMAkGA1UEBhMCREsxEzARBgNV
                BAgMClN5ZGRhbm1hcmsxCzAJBgNVBAcMAk5BMREwDwYDVQQKDAhlU2NpZW5jZTER
                MA8GA1UECwwIZVNjaWVuY2UxDzANBgNVBAMMBnVjbG91ZDCCASIwDQYJKoZIhvcN
                AQEBBQADggEPADCCAQoCggEBAK8TIpxSyi1BwiiAFTDTHFVXSa/nHT882s22hikk
                6mljQohCqHXGpSTuINMM5ma5wdsqFfYb/nIz7fXdDktW/hAbhDIkoOLGxb4BJx+S
                /Ce3LZXSKlT8CxJ+Ayw66APG2ntksqQVkKvPD+HUpSEV5mXR+E+3uzj8Vd8e1SYi
                h/423zfJ8bJA7TSripi85BWzwMbWJYbLT4wW1PwOhNpwhqqClTjcnlfeqBb3SMmj
                pgKg5bM6YuZKyoSrKMF2WjzBxw1aOwBRKbO8Z12I8noFeGDw9+w/caYG+ZvusIEy
                oTk/+zhG8hRRyNa2RCAZspz06jaCUV1aFxX7Hl/yvRQAu6sCAwEAATANBgkqhkiG
                9w0BAQsFAAOCAQEAAmgal9lScwRkLMV2CSBhCcnog/PL5bIiQj0HieRkHb8gUiwk
                OGbOxDH2P/Gn/4WDO6SwQDXb8L/Kk3e+jDD8snt2n1Pqmw/7WgemTZoEQMVKuWGM
                TseyfMEA6PFA7OZTi4CMtfq/Qh0rlPN72wA3fxjOS8upku9X0S2gsLuNQorj8R/5
                iAHo/fPNAmAHVI8cyQRWbcP5K7TvEf3Ij9yBByas50AHoMdjCoSFqoIVtiT8JlMa
                0iRq2Uj2uVqPocy2tJ2K27FHKRR3H6YSdjzZ/Ys+9kxc22I40nLVLfCdnBV9/GGU
                a/e5GT6FiQPV79ntyelUEQCkqT7Xr0uSt4Cc2g==
                -----END CERTIFICATE-----
            """.trimIndent())
        }

        "production" -> {
            configure("host", "cloud.sdu.dk")
            configure("enablyWayf", true)
            configure("cert", """
                    -----BEGIN CERTIFICATE-----
                    MIIDNDCCAhwCCQCshN+wCG6lEzANBgkqhkiG9w0BAQsFADBcMQswCQYDVQQGEwJE
                    SzEdMBsGA1UECgwUU3lkZGFuc2sgVW5pdmVyc2l0ZXQxFzAVBgNVBAsMDmVTY2ll
                    bmNlQ2VudGVyMRUwEwYDVQQDDAxjbG91ZC5zZHUuZGswHhcNMTgxMTE1MTMwNjU2
                    WhcNMTkxMTE1MTMwNjU2WjBcMQswCQYDVQQGEwJESzEdMBsGA1UECgwUU3lkZGFu
                    c2sgVW5pdmVyc2l0ZXQxFzAVBgNVBAsMDmVTY2llbmNlQ2VudGVyMRUwEwYDVQQD
                    DAxjbG91ZC5zZHUuZGswggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQCf
                    ZMpChrOcUx4sPr3FXrpJ6n77H0ueHXzMIGKyh7+JZuScB77eMwWa6MCeo58byzPu
                    1u6Je6W3QWt/vWYNFwj+yAFv9FRjh67mpB7v4Tew6P1HcIrSBE6P+cOdtDO1augf
                    fAI8K77FuVC7ZVlTWwP2wjOQIvBTOEtoTN1IOAlmbFwRkX+rRwZ1U53ZNo17PW0T
                    QHxdD90NbYqx/wuFus1UdgBrI0uVTOmJG7ohiWt8bpW5mz+et4SGgFGl2LD6mv4l
                    etHzhOJgMMVEAA8o5TwwxCYw5QaGdLtZ1jPTfWj3w0wJxPTcPj39unI/ztfrW+OG
                    efHsK02igOfRKv8rbKcJAgMBAAEwDQYJKoZIhvcNAQELBQADggEBAG4KYesD3ZM/
                    fh5lHFd6BcCCC6n9+TkeeFqNoWnmeQN6HZAv7X4OdQ3wyMV2fbeGWRG23OjqcTud
                    5Z1NfXZPoVCq+PeG1PcUsgA5iTSNpPENGEJHp1fVFJIrRjaaVUYa5mB58M8x29Hi
                    52DnIKUth4svRL5JtikuNEtFWOUmoX4QNrgxPGyRaqGwWNXD5EUMRgVeaq97rBB1
                    euWW6VhEvo5S5p64K0E1EjGHv3N384/Nu8+P7sKX3vQorNiidnSJlMl+VARcV6k9
                    eWK+YvfER32gylkRqG56k2oC9AuRKV88mLVCV7HcpA2Q1gDIqRVXhMavgFZ+Mxh+
                    Ms12PEWBG3Q=
                    -----END CERTIFICATE-----               
            """.trimIndent())
        }
    }

    configure("tosVersion", 1)
    configure("tos",
"""
SDU eScience Terms of Service
=============================

*Version 1.3*

*Last modified: 3 December 2020*

## SDU eScience Service Agreement

This SDU eScience Terms of Service Agreement (the "Agreement") is made and entered into by and between the University of Southern Denmark (SDU) and the entity or person agreeing to these terms ("Customer").

This Agreement is effective as of the date Customer clicks to accept the Agreement (the "Effective Date"). If you are accepting on behalf of Customer, you represent and warrant that: (i) you have full legal authority to bind Customer to this Agreement; (ii) you have read and understand this Agreement; and (iii) you agree, on behalf of Customer, to this Agreement. If you do not have the legal authority to bind Customer, please do not click to accept. This Agreement governs Customer's access to and use of the Service.

### 1. **Provision of the Services**

1.1 **Services Use.** Subject to this Agreement, during the Term, Customer may: (a) use the Services, (b) integrate the Services into any Application that has material value independent of the Services, and (c) use any Software provided by SDU eScience as part of the Services. Customer may not sublicense or transfer these rights except as permitted under the Assignment section of the Agreement.

1.2 **Facilities.**  All facilities used to store and process an Application and Customer Data will adhere to Best Practice security standards.

1.3 **Data Location.** Customer Data will be stored at the SDU eScience data center. By using the Services, Customer consents to this storage of Customer Data.

1.4 **Accounts.** Customer must have an Account and a Token (if applicable) to use the Services, and is responsible for the information it provides to create the Account, the security of the Token and its passwords for the Account, and for any use of its Account and the Token. If Customer becomes aware of any unauthorized use of its password, its Account or the Token, Customer will notify SDU eScience as promptly as possible. SDU eScience has no obligation to provide Customer multiple Tokens or Accounts.

1.5 **New Applications and Services.** SDU eScience may: (i) make new applications, tools, features or functionality available from time to time through the Services and (ii) add new services to the "Services" definition from time to time (by adding them at the URL set forth under that definition), the use of which may be contingent upon Customer's agreement to additional terms.

1.6 **Modifications.**

a. **To the Services.** SDU eScience may make reasonable updates to the Services from time to time. If SDU eScience makes a material change to the Services, SDU eScience will inform Customer.

b. **To the Agreement.** SDU eScience may make changes to this Agreement, including pricing (and any linked documents) from time to time. Unless otherwise noted by SDU eScience, material changes to the Agreement will become effective 30 days after they are posted, except if the changes apply to new functionality in which case they will be effective immediately. SDU eScience will provide at least 90 days' advance notice for materially adverse changes to any SLAs by either: (i) sending an email to Customer's primary point of contact; (ii) posting a notice in the Services' notification systems if applicable; or (iii) posting a notice to the applicable SLA webpage. If Customer does not agree to the revised Agreement, please stop using the Services. SDU eScience will post any modification to this Agreement to the Terms URL.

c. **To the Data Processing and Security Terms.** SDU eScience may only change the Data Processing and Security Terms where such change is required to comply with applicable law, applicable regulation, court order, or guidance issued by a governmental regulator or agency, where such change is expressly permitted by the Data Processing and Security Terms, or where such change:

(i) does not result in a degradation of the overall security of the Services;

(ii) does not expand the scope of or remove any restrictions on SDU eScience's processing of Customer Personal Data, as described in Section 5.2 (Scope of Processing) of the Data Processing and Security Terms; and

(iii) does not otherwise have a material adverse impact on Customer's rights under the Data Processing and Security Terms.

If SDU eScience makes a material change to the Data Processing and Security Terms in accordance with this Section, SDU eScience will post the modification to the URL containing those terms.

1.7 **Service Specific Terms and Data Processing and Security Terms.** The Service Specific Terms and Data Processing and Security Terms are incorporated by this reference into the Agreement.

### 2. **Payment Terms**

2.1 **Free Quota.** Certain Services are provided to Customer without charge up to the Fee Threshold, as applicable.

2.2 **Online Billing.** At the end of the applicable Fee Accrual Period, SDU eScience will issue an electronic bill to Customer for all charges accrued above the Fee Threshold based on (i) Customer's use of the Services during the previous Fee Accrual Period (including, if any, the relevant Fee for TSS set forth in the Fees definition below); (ii) any Reserved Units selected; (iii) any Committed Purchases selected; and/or (iv) any Package Purchases selected. For use above the Fee Threshold, Customer will be responsible for all Fees up to the amount set in the Account and will pay all Fees in the currency set forth in the invoice. Customer's obligation to pay all Fees is non-cancellable. SDU eScience's measurement of Customer's use of the Services is final. SDU eScience has no obligation to provide multiple bills.

2.3 **Invoice Disputes & Refunds.** Any invoice disputes must be submitted prior to the payment due date. If the parties determine that certain billing inaccuracies are attributable to SDU eScience, SDU eScience will not issue a corrected invoice, but will instead issue a credit memo specifying the incorrect amount in the affected invoice. If the disputed invoice has not yet been paid, SDU eScience will apply the credit memo amount to the disputed invoice and Customer will be responsible for paying the resulting net balance due on that invoice. To the fullest extent permitted by law, Customer waives all claims relating to Fees unless claimed within sixty days after charged (this does not affect any Customer rights with its credit card issuer). Refunds (if any) are at the discretion of SDU eScience and will only be in the form of credit for the Services. Nothing in this Agreement obligates SDU eScience to extend credit to any party.

2.4 **Delinquent Payments; Suspension.** Late payments may bear interest at the rate of 1.5% per month (or the highest rate permitted by law, if less) from the payment due date until paid in full. Customer will be responsible for all reasonable expenses (including attorneys' fees) incurred by SDU eScience in collecting such delinquent amounts. If Customer is late on payment for the Services, SDU eScience may Suspend the Services or terminate the Agreement for breach pursuant to Section 9.2.

2.5 **No Purchase Order Number Required.** For clarity, Customer is obligated to pay all applicable Fees without any requirement for SDU eScience to provide a purchase order number on SDU eScience's invoice (or otherwise).

### 3. **Customer Obligations**

3.1 **Compliance.** Customer will (a) ensure that Customer and its End Users' use of the Services complies with the Agreement, (b) use commercially reasonable efforts to prevent and terminate any unauthorized use of, or access to, the Services, and (c) promptly notify SDU eScience Center of any unauthorized use of, or access to, the Services, Account, or Customer's password of which Customer becomes aware. SDU eScience Center reserves the right to investigate any potential violation of the AUP by Customer, which may include reviewing Customer Applications, Customer Data, or Projects.

3.2 **Privacy.** Customer will obtain and maintain any required consents necessary to permit the processing of Customer Data under this Agreement.

3.3 **Restrictions.**  Customer will not, and will not allow End Users to, (a) copy, modify, or create a derivative work of the Services; (b) reverse engineer, decompile, translate, disassemble, or otherwise attempt to extract any or all of the source code of the Services (except to the extent such restriction is expressly prohibited by applicable law); (c) sell, resell, sublicense, transfer, or distribute any or all of the Services; or (d) access or use the Services (i) for High Risk Activities; (ii) in violation of the AUP; (iii) in a manner intended to avoid incurring Fees (including creating multiple Customer Applications, Accounts, or Projects to simulate or act as a single Customer Application, Account, or Project (respectively)) or to circumvent Service-specific usage limits or quotas; (iv) to engage in cryptocurrency mining without SDU eScience Center's prior written approval; (v) to operate or enable any telecommunications service or in connection with any Customer Application that allows Customer End Users to place calls or to receive calls from any public switched telephone network, unless otherwise described in the Service Specific Terms; (vi) for materials or activities that are subject to the International Traffic in Arms Regulations (ITAR) maintained by the United States Department of State; (vii) in a manner that breaches, or causes the breach of, Export Control Laws; or (viii) to transmit, store, or process health information subject to United States HIPAA regulations except as permitted by an executed HIPAA BAA.

3.4 **Third Party Components.** Third party components (which may include open source software) of the Services may be subject to separate license agreements. To the limited extent a third party license expressly supersedes this Agreement, that third party license governs Customer's use of that third party component.

3.5 **Documentation.** SDU eScience may provide Documentation for Customer's use of the Services. The Documentation may specify restrictions (e.g. attribution or HTML restrictions) on how the Applications may be built or the Services may be used and Customer will comply with any such restrictions specified.

### 4. **Suspension**

4.1 **AUP Violations.** If SDU eScience becomes aware that Customer's or any Customer End User's use of the Services violates the AUP, SDU eScience will give Customer notice of the violation by requesting that Customer correct the violation. If Customer fails to correct the violation within 24 hours of SDU eScience's request, then SDU eScience may Suspend all or part of Customer's use of the Services until the violation is corrected.

4.2 **Other Suspension.** Notwithstanding Section 4.1 (AUP Violations) SDU eScience may immediately Suspend all or part of Customer's use of the Services if: (a) SDU eScience believes Customer's or any Customer End User's use of the Services could adversely impact the Services, other customers' or their end users' use of the Services, or the SDU eScience network or servers used to provide the Services, which may include use of the Services for cryptocurrency mining without SDU eScience's prior written approval; (b) there is suspected unauthorized third-party access to the Services; (c) SDU eScience believes it is required to Suspend immediately to comply with applicable law; or (d) Customer is in breach of Section 3.3 (Restrictions). SDU eScience will lift any such Suspension when the circumstances giving rise to the Suspension have been resolved. At Customer's request, unless prohibited by applicable law, SDU eScience will notify Customer of the basis for the Suspension as soon as is reasonably possible.

### 5. **Intellectual Property Rights; Use of Customer Data; Feedback; Benchmarking**

5.1 **Intellectual Property Rights.** Except as expressly set forth in this Agreement, this Agreement does not grant either party any rights, implied or otherwise, to the other's content or any of the other's intellectual property. As between the parties, Customer owns all Intellectual Property Rights in Customer Data and the Application or Project (if applicable), and SDU eScience owns all Intellectual Property Rights in the Services and Software.

5.2 **Use of Customer Data.** SDU eScience will not access or use Customer Data, except as necessary to provide the Services and TSS to Customer.

5.3 **Customer Feedback.** If Customer provides SDU eScience Feedback about the Services, then SDU eScience may use that information without obligation to Customer, and Customer hereby irrevocably assigns to SDU eScience all right, title, and interest in that Feedback.

### 6. **Technical Support Services**

6.1 **By Customer.** Customer is responsible for technical support of its Applications and Projects.

6.2 **By SDU eScience.** Subject to payment of applicable support Fees, SDU eScience will provide TSS to Customer during the Term in accordance with the TSS Guidelines.

### 7. **Deprecation of Services**

7.1 **Discontinuance of Services.** Subject to Section 7.2, SDU eScience may discontinue any Services or any portion or feature for any reason at any time without liability to Customer.

7.2 **Deprecation Policy.** SDU eScience will announce if it intends to discontinue or make backwards incompatible changes to the Services specified at the URL in the next sentence. SDU eScience will use reasonable efforts to continue to operate those Services versions and features identified at https://legal.cloud.sdu.dk/terms/deprecation without these changes for at least three months after that announcement, unless (as SDU eScience determines in its reasonable good faith judgment):

(i) required by law or third party relationship (including if there is a change in applicable law or relationship), or

(ii) doing so could create a security risk or substantial economic or material technical burden.

The above policy is the "Deprecation Policy."

### 8. **Confidential Information**

8.1 **Obligations.** The recipient will not disclose the Confidential Information, except to Affiliates, employees, agents or professional advisors who need to know it and who have agreed in writing (or in the case of professional advisors are otherwise bound) to keep it confidential. The recipient will ensure that those people and entities use the received Confidential Information only to exercise rights and fulfill obligations under this Agreement, while using reasonable care to keep it confidential.

8.2 **Required Disclosure.** Notwithstanding any provision to the contrary in this Agreement, the recipient may also disclose Confidential Information to the extent required by applicable Legal Process; provided that the recipient uses commercially reasonable efforts to: (i) promptly notify the other party of such disclosure before disclosing; and (ii) comply with the other party's reasonable requests regarding its efforts to oppose the disclosure. Notwithstanding the foregoing, subsections (i) and (ii) above will not apply if the recipient determines that complying with (i) and (ii) could: (a) result in a violation of Legal Process; (b) obstruct a governmental investigation; and/or (c) lead to death or serious physical harm to an individual. As between the parties, Customer is responsible for responding to all third party requests concerning its use and Customer End Users' use of the Services.

### 9. **Term and Termination**

9.1 **Agreement Term.** The "Term" of this Agreement will begin on the Effective Date and continue until the Agreement is terminated as set forth in Section 9 of this Agreement.

9.2 **Termination for Breach.** Either party may terminate this Agreement for breach if: (i) the other party is in material breach of the Agreement and fails to cure that breach within thirty days after receipt of written notice; (ii) the other party ceases its business operations or becomes subject to insolvency proceedings; or (iii) the other party is in material breach of this Agreement more than two times notwithstanding any cure of such breaches. In addition, SDU eScience may terminate any, all, or any portion of the Services or Projects, if Customer meets any of the conditions in Section 9.2(i), (ii), and/or (iii).

9.3 **Termination for Inactivity.** SDU eScience reserves the right to terminate the provision of the Service(s) to a Project upon 30 days advance notice if, for a period of 60 days (i) Customer has not accessed the Service(s); and (ii) such Project has not incurred any Fees for such Service(s).

9.4 **Termination for Convenience.** Customer may stop using the Services at any time. Customer may terminate this Agreement for its convenience at any time on prior written notice and upon termination, must cease use of the applicable Services. SDU eScience may terminate this Agreement for its convenience at any time without liability to Customer to the extent permitted by law.

9.5 **Effect of Termination.** If the Agreement is terminated, then: (i) the rights granted by one party to the other will immediately cease; (ii) all Fees owed by Customer to SDU eScience are immediately due upon receipt of the final electronic bill; (iii) Customer will delete the Software, any Application, Instance, Project, and any Customer Data; and (iv) upon request, each party will use reasonable efforts to return or destroy all Confidential Information of the other party.

### 10. **Representations and Warranties**

Each party represents and warrants that: (a) it has full power and authority to enter into the Agreement; and (b) it will comply with all laws and regulations applicable to its provision, or use, of the Services, as applicable. SDU eScience warrants that it will provide the Services in accordance with the applicable SLA (if any).

### 11. **Disclaimer**

EXCEPT AS EXPRESSLY PROVIDED FOR IN THIS AGREEMENT, TO THE MAXIMUM EXTENT PERMITTED BY APPLICABLE LAW: (a) SDU ESCIENCE AND ITS SUPPLIERS DO NOT MAKE ANY OTHER WARRANTY OF ANY KIND, WHETHER EXPRESS, IMPLIED, STATUTORY OR OTHERWISE, INCLUDING WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR USE AND NONINFRINGEMENT; (b) SDU ESCIENCE AND ITS SUPPLIERS ARE NOT RESPONSIBLE OR LIABLE FOR THE DELETION OF OR FAILURE TO STORE ANY CUSTOMER DATA AND OTHER COMMUNICATIONS MAINTAINED OR TRANSMITTED THROUGH USE OF THE SERVICES; (c) CUSTOMER IS SOLELY RESPONSIBLE FOR SECURING AND BACKING UP ITS APPLICATION, PROJECT, AND CUSTOMER DATA; AND (d) NEITHER SDU ESCIENCE NOR ITS SUPPLIERS, WARRANTS THAT THE OPERATION OF THE SOFTWARE OR THE SERVICES WILL BE ERROR-FREE OR UNINTERRUPTED. NEITHER THE SOFTWARE NOR THE SERVICES ARE DESIGNED, MANUFACTURED, OR INTENDED FOR HIGH RISK ACTIVITIES.

### 12. **Limitation of Liability**

12.1 **SDU eScience Center Indemnification Obligations.** SDU eScience Center will defend Customer and its Affiliates participating under the Agreement ("Customer Indemnified Parties") and indemnify them against Indemnified Liabilities in any Third-Party Legal Proceeding to the extent arising from an allegation that any Service or any SDU eScience Center Brand Feature infringes the third party's Intellectual Property Rights.

12.2 **Customer Indemnification Obligations.** Customer will defend SDU eScience Center and its Affiliates participating under this Agreement and indemnify them against Indemnified Liabilities in any Third-Party Legal Proceeding to the extent arising from (a) any Customer Application, Project, Customer Data, or Customer Brand Features; or (b) Customer's or an End User's use of the Services in breach of the AUP or Section 3.3 (Restrictions).

### 13. **Indemnification**

13.1 **Remedies.**

a. If SDU eScience reasonably believes the Services might infringe a third party's Intellectual Property Rights, then SDU eScience may, at its sole option and expense: (a) procure the right for Customer to continue using the Services; (b) modify the Services to make them non-infringing without materially reducing their functionality; or (c) replace the Services with a non-infringing, functionally equivalent alternative.

b. If SDU eScience does not believe the remedies in Section 13.1(a) are reasonable, then SDU eScience may suspend or terminate Customer's use of the impacted Services.

### 14. **Miscellaneous**

14.1 **Assignment.** Neither party may assign any part of this Agreement without the written consent of the other, except to an Affiliate where: (a) the assignee has agreed in writing to be bound by the terms of this Agreement; (b) the assigning party remains liable for obligations under the Agreement if the assignee defaults on them; and (c) the assigning party has notified the other party of the assignment. Any other attempt to assign is void.

14.2 **Change of Control.** If a party experiences a change of Control (for example, through a stock purchase or sale, merger, or other form of corporate transaction): (a) that party will give written notice to the other party within thirty days after the change of Control; and (b) the other party may immediately terminate this Agreement any time between the change of Control and thirty days after it receives that written notice.

14.3 **Force Majeure.** Neither party will be liable for failure or delay in performance to the extent caused by circumstances beyond its reasonable control.

14.4 **No Agency.** This Agreement does not create any agency, partnership or joint venture between the parties.

14.5 **No Waiver.** Neither party will be treated as having waived any rights by not exercising (or delaying the exercise of) any rights under this Agreement.

14.6 **Severability.** If any term (or part of a term) of this Agreement is invalid, illegal, or unenforceable, the rest of the Agreement will remain in effect.

14.7 **No Third-Party Beneficiaries.** This Agreement does not confer any benefits on any third party unless it expressly states that it does.

14.8 **Equitable Relief.** Nothing in this Agreement will limit either party's ability to seek equitable relief.

14.9 **Danish Governing Law and Jurisdiction.**

a. This Agreement shall be governed and construed in accordance with the laws of Denmark.

b. Any dispute arising in connection with this Agreement or the breach thereof, shall, in the absence of an amicable solution by the parties, belong to the exclusive jurisdiction of the competent courts in Odense, Denmark.

14.10 **Amendments.** Except as set forth in Section 1.6(b) or (c), any amendment must be in writing, signed by both parties, and expressly state that it is amending this Agreement.

14.11 **Survival.** The following Sections will survive expiration or termination of this Agreement: 5, 8, 9.5, 12, 13, and 14.

14.12 **Entire Agreement.** This Agreement sets out all terms agreed between the parties and supersedes all other agreements between the parties relating to its subject matter. In entering into this Agreement, neither party has relied on, and neither party will have any right or remedy based on, any statement, representation or warranty (whether made negligently or innocently), except those expressly set out in this Agreement. The terms located at a URL referenced in this Agreement and the Documentation are incorporated by reference into the Agreement. After the Effective Date, SDU eScience may provide an updated URL in place of any URL in this Agreement.

14.13 **Conflicting Terms.** If there is a conflict between the documents that make up this Agreement, the documents will control in the following order: the Agreement, and the terms at any URL.

14.14 **Definitions.**

**"Account"** means Customer's account at SDU eScience.

**"Affiliate"** means any entity that directly or indirectly Controls, is Controlled by, or is under common Control with a party.

**"Allegation"** means an unaffiliated third party's allegation.

**"Application(s)"** means any web or other application Customer creates using the Services, including any source code written by Customer to be used with the Services, or hosted in an Instance.

**"AUP"** means the acceptable use policy set forth here for the Services: http://legal.cloud.sdu.dk/terms/aup.

**"Brand Features"** means the trade names, trademarks, service marks, logos, domain names, and other distinctive brand features of each party, respectively, as secured by such party from time to time.

**"Committed Purchase(s)"** have the meaning set forth in the Service Specific Terms.

**"Confidential Information"** means information that one party (or an Affiliate) discloses to the other party under this Agreement, and which is marked as confidential or would normally under the circumstances be considered confidential information. It does not include information that is independently developed by the recipient, is rightfully given to the recipient by a third party without confidentiality obligations, or becomes public through no fault of the recipient. Subject to the preceding sentence, Customer Data is considered Customer's Confidential Information.

**"Control"** means control of greater than fifty percent of the voting rights or equity interests of a party.

**"Customer Data"** means content provided to SDU eScience by Customer (or at its direction) via the Services under the Account.

**"Customer End Users"** means the individuals Customer permits to use the Application.

**"Data Processing and Security Terms"** means the terms set forth at: https://legal.cloud.sdu.dk/terms/data-processing-terms.

**"Documentation"** means the SDU eScience documentation (as may be updated from time to time) in the form generally made available by SDU eScience to its customers for use with the Services at https://docs.cloud.sdu.dk/.

**"Fee Accrual Period"** means a calendar month or another period specified by SDU eScience in the Service Specific Terms.

**"Fee Threshold"** means the threshold (as may be updated from time to time), as applicable for certain Services, as set forth Service Specific Terms. If not explicitly defined in the Service Specific Terms, the Fee Threshold is not applicable.

**"Feedback"** means feedback or suggestions about the Services provided to SDU eScience by Customer.

**"Fees"** means the applicable fees for each Service and any applicable Taxes. The Fees for each Service are set forth here: https://cloud.sdu.dk/app/skus.

**"High Risk Activities"** means activities where the use or failure of the Services could lead to death, personal injury, or environmental damage (such as operation of nuclear facilities, air traffic control, life support systems, or weaponry).

**"HIPAA"** means the Health Insurance Portability and Accountability Act of 1996 as it may be amended from time to time, and any regulations issued under it.

**"Indemnified Liabilities"** means any (i) settlement amounts approved by the indemnifying party; and (ii) damages and costs finally awarded against the indemnified party and its Affiliates by a court of competent jurisdiction.

**"Instance"** means a virtual machine or container instance, configured and managed by Customer, which runs on the Services. Instances are more fully described in the Documentation.

**"Intellectual Property Rights"** means current and future worldwide rights under patent, copyright, trade secret, trademark, and moral rights laws, and other similar rights.

**"Legal Process"** means a data disclosure request made under law, governmental regulation, court order, subpoena, warrant, governmental regulatory or agency request, or other valid legal authority, legal procedure, or similar process.

**"Package Purchase"** has the meaning set forth in the Service Specific Terms.

**"Project"** means a grouping of computing, storage, and API resources for Customer, and via which Customer may use the Services. Projects are more fully described in the Documentation.

**"Reserved Capacity Units"** have the meaning set forth in the Service Specific Terms.

**"Reserved Unit Term"** has the meaning set forth in the Service Specific Terms.

**"Reserved Units"** have the meaning set forth in the Service Specific Terms.

**"Service Specific Terms"** means the terms specific to one or more Services set forth here: https://legal.cloud.sdu.dk/terms/service-terms.

**"Services"** means the services as set forth here: https://legal.cloud.sdu.dk/terms/services (including any associated APIs).

**"SLA"** means each of the then-current service level agreements at: https://legal.cloud.sdu.dk/terms/sla.

**"Software"** means any downloadable tools, software development kits or other such proprietary computer software provided by SDU eScience in connection with the Services, which may be downloaded by Customer, and any updates SDU eScience may make to such Software from time to time.

**"Suspend" or "Suspension"** means disabling or limiting access to or use of the Services or components of the Services.

**"Taxes"** means any duties, customs fees, or taxes (other than SDU's income tax) associated with the purchase of the Services, including any related penalties or interest.

**"Term"** has the meaning set forth in Section 9 of this Agreement.

**"Terms URL"** means the following URL set forth here: https://legal.cloud.sdu.dk/terms.

**"Third-Party Legal Proceeding"** means any formal legal proceeding filed by an unaffiliated third party before a court or government tribunal (including any appellate proceeding).

**"Token"** means an alphanumeric key that is uniquely associated with Customer's Account.

**"Trademark Guidelines"** means SDU eScience's Guidelines for Third Party Use of SDU eScience Brand Features, located at: https://www.sdu.dk/da/nyheder/presserummet/logo_og_designguide.

**"TSS"** means the technical support service provided by SDU eScience to the administrators under the TSS Guidelines.

**"TSS Guidelines"** means SDU eScience's technical support services guidelines then in effect for the Services. TSS Guidelines are at the following URL: https://legal.cloud.sdu.dk/terms/tssg.



## License
This document is a derivative of ["Google Cloud Platform Terms of Service"](https://cloud.google.com/terms) by [Google](https://www.google.com), used under [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/). This document is licensed under [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/) by the [SDU eScience center](https://escience.sdu.dk).

""")
}

config("indexing") { ctx ->
    when (ctx.environment) {
        "development" -> {
            configure("numberOfShards", 5)
            configure("numberOfReplicas", 2)
        }

        "test" -> {
            configure("numberOfShards", 2)
            configure("numberOfReplicas", 1)
        }

        "production" -> {
            configure("numberOfShards", 5)
            configure("numberOfReplicas", 2)
        }
    }
}

