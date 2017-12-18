<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">
    <meta name="description" content="Bootstrap Admin Template">
    <meta name="keywords" content="app, responsive, jquery, bootstrap, dashboard, admin">
    <title>eScienceCloud - ${title}</title>
    <!-- Vendor styles-->
    <!-- build:css(../app) css/vendor.css-->
    <!-- Animate.CSS-->
    <link rel="stylesheet" href="/vendor/animate.css/animate.css">
    <!-- Bootstrap-->
    <link rel="stylesheet" href="/vendor/bootstrap/dist/css/bootstrap.min.css">
    <!-- Ionicons-->
    <link rel="stylesheet" href="/vendor/ionicons/css/ionicons.css">
    <!-- Bluimp Gallery-->
    <link rel="stylesheet" href="/vendor/blueimp-gallery/css/blueimp-gallery.css">
    <link rel="stylesheet" href="/vendor/blueimp-gallery/css/blueimp-gallery-indicator.css">
    <link rel="stylesheet" href="/vendor/blueimp-gallery/css/blueimp-gallery-video.css">
    <!-- Datepicker-->
    <link rel="stylesheet" href="/vendor/bootstrap-datepicker/dist/css/bootstrap-datepicker3.css">
    <!-- Rickshaw-->
    <link rel="stylesheet" href="/vendor/rickshaw/rickshaw.css">
    <!-- Select2-->
    <link rel="stylesheet" href="/vendor/select2/dist/css/select2.css">
    <!-- Clockpicker-->
    <link rel="stylesheet" href="/vendor/clockpicker/dist/bootstrap-clockpicker.css">
    <!-- Range Slider-->
    <link rel="stylesheet" href="/vendor/nouislider/distribute/nouislider.min.css">
    <!-- ColorPicker-->
    <link rel="stylesheet" href="/vendor/mjolnic-bootstrap-colorpicker/dist/css/bootstrap-colorpicker.css">
    <!-- Summernote-->
    <link rel="stylesheet" href="/vendor/summernote/dist/summernote.css">
    <!-- Dropzone-->
    <link rel="stylesheet" href="/vendor/dropzone/dist/basic.css">
    <link rel="stylesheet" href="/vendor/dropzone/dist/dropzone.css">
    <!-- Xeditable-->
    <link rel="stylesheet" href="/vendor/x-editable/dist/bootstrap3-editable/css/bootstrap-editable.css">
    <!-- Bootgrid-->
    <link rel="stylesheet" href="/vendor/jquery.bootgrid/dist/jquery.bootgrid.css">
    <!-- Datatables-->
    <link rel="stylesheet" href="/vendor/datatables/media/css/jquery.dataTables.css">
    <!-- Sweet Alert-->
    <link rel="stylesheet" href="/vendor/sweetalert/dist/sweetalert.css">
    <!-- Loaders.CSS-->
    <link rel="stylesheet" href="/vendor/loaders.css/loaders.css">
    <!-- Material Floating Button-->
    <link rel="stylesheet" href="/vendor/ng-material-floating-button/mfb/dist/mfb.css">
    <!-- Material Colors-->
    <link rel="stylesheet" href="/vendor/material-colors/dist/colors.css">
    <!-- endbuild-->
    <!-- Application styles-->
    <link rel="stylesheet" href="/css/app.css">
    <style>
        [v-cloak] {
            display: none;
        }
    </style>
</head>
<body class="theme-1">
<div class="layout-container">
    <!-- top navbar-->
    <header class="header-container">
        <nav>
            <ul class="visible-xs visible-sm">
                <li><a id="sidebar-toggler" href="#" class="menu-link menu-link-slide"><span><em></em></span></a></li>
            </ul>
            <ul class="hidden-xs">
                <li><a id="offcanvas-toggler" href="#" class="menu-link menu-link-slide"><span><em></em></span></a></li>
            </ul>
            <h2 class="header-title">${title}</h2>
            <ul class="pull-right">
                <li><button class="btn btn-info">Status: No issues</button></li>
                <li><a id="header-search" href="#" class="ripple"><em class="ion-ios-search-strong"></em></a></li>
                <li class="dropdown"><a href="#" data-toggle="dropdown" class="dropdown-toggle has-badge ripple"><em
                        class="ion-person"></em><sup class="badge bg-danger"></sup></a>
                    <ul class="dropdown-menu dropdown-menu-right md-dropdown-menu">
                        <li><a href="/logout"><em class="ion-log-out icon-fw"></em>Logout</a></li>
                    </ul>
                </li>
            </ul>
        </nav>
    </header>
    <!-- sidebar-->
    <aside class="sidebar-container">
        <div class="sidebar-header">
            <div class="pull-right pt-lg text-muted hidden"><em class="ion-close-round"></em></div>
            <a href="/" class="sidebar-header-logo"><img src="/app/img/logo.png" data-svg-replace="/img/logo.svg"
                                                         alt="Logo"><span
                    class="sidebar-header-logo-text">SDUCloud</span></a>
        </div>
        <div class="sidebar-content">
            <div class="sidebar-toolbar text-center"><a href=""><img src="/img/user/01.jpg" alt="Profile"
                                                                     class="img-circle thumb64"></a>
                <div class="mt">Welcome, ${name}</div>
            </div>
            <nav class="sidebar-nav">
                <ul>
                <#list options as option>
                    <#if option.href??>
                        <li><a href="${option.href}" class="ripple"><span class="pull-right nav-label"></span>
                            <span class="nav-icon"><img src=""
                                                        data-svg-replace="/img/icons/aperture.svg"
                                                        alt="MenuItem"
                                                        class="hidden"></span><span>${option.name}</span>
                        </a></li>
                    <#else>
                    <li><a href="#" class="ripple"><span class="pull-right nav-caret"><em
                            class="ion-ios-arrow-right"></em></span><span class="pull-right nav-label"></span><span
                            class="nav-icon"><img src="" data-svg-replace="/img/icons/connection-bars.svg" alt="MenuItem"
                                                  class="hidden"></span><span>${option.name}</span></a>
                        <ul class="sidebar-subnav">
                            <#list option.children as child>
                                <li><a href="${child.href}" class="ripple"><span
                                        class="pull-right nav-label"></span><span>${child.name}</span></a></li>
                            </#list>
                        </ul>
                    </#if>
                </#list>
                </ul>
            </nav>
        </div>
    </aside>
    <div class="sidebar-layout-obfuscator"></div>
    <main class="main-container" id="main">