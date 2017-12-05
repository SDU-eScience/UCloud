# eScienceCloudUi

This is the main repository for eScienceCloudUi the web based user interface for eScienceCloud.
eScienceCloud is the initiative from the eScience Center at the University of Southern Denmark.

The technology used is:

- Ktor (Jetbrains Kotlin based web framework)
- Webpack (Through NPM)
- VueJS

## Running

As the server uses Webpack to serve assets, in addition to launching the KTOR project, go to the app folder and run node using:

```javascript
npm run dev
```

The website will be available at localhost:9090

### Issues

Currently, sessions persist in the browser between restarts of the frontend server, which can lead to inconsistent data retrieval.

Current workarounds:
    - Either use private browsing
    - Delete cookies/sessions for the site

to be added....
