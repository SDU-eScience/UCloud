insert into app_store.tools
    (name, version, created_at, modified_at, original_document, owner, tool)
values
    (
        'unknown', 'unknown',
        now(), now(),
        '{}',
        'ghost',
        $$
        {
          "info": {
            "name": "unknown",
            "version": "unknown"
          },
          "image": "alpine:3",
          "title": "Unknown Application",
          "authors": [
            "UCloud"
          ],
          "backend": "DOCKER",
          "license": "",
          "container": "alpine:3",
          "description": "This job was started outside of UCloud or with an application which no longer exists.",
          "requiredModules": [],
          "supportedProviders": null,
          "defaultNumberOfNodes": 1,
          "defaultTimeAllocation": {
            "hours": 1,
            "minutes": 0,
            "seconds": 0
          }
        }
        $$
    );

insert into app_store.applications
    (name, version, created_at, modified_at, original_document, owner, tool_name, tool_version, authors, tags, title, description, website, application)
values
    (
        'unknown', 'unknown',
        now(), now(),
        '{}',
        'ghost',
        'unknown', 'unknown',
        '["UCloud"]',
        '[]',
        'Unknown',
        'This job was started outside of UCloud or with an application which no longer exists.',
        null,
        $$
        {
          "vnc": null,
          "web": null,
          "tool": {
            "name": "unknown",
            "tool": null,
            "version": "unknown"
          },
          "container": {
            "runAsRoot": false,
            "runAsRealUser": false,
            "changeWorkingDirectory": true
          },
          "invocation": [
            {
              "type": "word",
              "word": "sh"
            },
            {
              "type": "word",
              "word": "-c"
            },
            {
              "type": "word",
              "word": "exit 0"
            }
          ],
          "parameters": [],
          "environment": null,
          "allowPublicIp": false,
          "allowMultiNode": true,
          "fileExtensions": [],
          "licenseServers": [],
          "allowPublicLink": false,
          "applicationType": "BATCH",
          "outputFileGlobs": [
            "*"
          ],
          "allowAdditionalPeers": null,
          "allowAdditionalMounts": null
        }
        $$
    );
