<#include "dashboard_header.ftl">
<script>
    window.application = {
        name: '${appName}',
        version: '${appVersion}'
    }
</script>
<div id="app"></div>
<script src="/frontend/runApp.js"></script>

<#include "dashboard_end.ftl">