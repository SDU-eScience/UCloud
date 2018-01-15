function FlotCharts() {

    if (!$.fn.plot) return;

    // Dont run if charts page not loaded
    if (!$('#bar-flotchart').length) return;

    // BAR
    // -----------------------------------
    $.get('server/chart/bar.json', function(data) {

        var barData = data;
        var barOptions = {
            series: {
                bars: {
                    align: 'center',
                    lineWidth: 0,
                    show: true,
                    barWidth: 0.6,
                    fill: true,
                    fillColor: {
                        colors: [{
                            opacity: 0.8
                        }, {
                            opacity: 0.5
                        }]
                    }
                }
            },
            grid: {
                borderColor: 'rgba(162,162,162,.26)',
                borderWidth: 1,
                hoverable: true,
                backgroundColor: 'transparent'
            },
            tooltip: true,
            tooltipOpts: {
                content: function(label, x, y) {
                    return x + ' : ' + y;
                }
            },
            xaxis: {
                tickColor: 'rgba(162,162,162,.26)',
                font: {
                    color: Colors.byName('blueGrey-200')
                },
                mode: 'categories'
            },
            yaxis: {
                // position: (isRTL ? 'right' : 'left'),
                tickColor: 'rgba(162,162,162,.26)',
                font: {
                    color: Colors.byName('blueGrey-200')
                }
            },
            shadowSize: 0
        };

        $('#bar-flotchart').plot(barData, barOptions);
    });



    // BAR STACKED
    // -----------------------------------
    $.get('server/chart/barstacked.json', function(data) {

        var barStackeData = data;
        var barStackedOptions = {
            series: {
                stack: true,
                bars: {
                    align: 'center',
                    lineWidth: 0,
                    show: true,
                    barWidth: 0.6,
                    fill: 0.9
                }
            },
            grid: {
                borderColor: 'rgba(162,162,162,.26)',
                borderWidth: 1,
                hoverable: true,
                backgroundColor: 'transparent'
            },
            tooltip: true,
            tooltipOpts: {
                content: function(label, x, y) {
                    return x + ' : ' + y;
                }
            },
            xaxis: {
                tickColor: 'rgba(162,162,162,.26)',
                font: {
                    color: Colors.byName('blueGrey-200')
                },
                mode: 'categories'
            },
            yaxis: {
                min: 0,
                max: 200, // optional: use it for a clear represetation
                // position: (isRTL ? 'right' : 'left'),
                tickColor: 'rgba(162,162,162,.26)',
                font: {
                    color: Colors.byName('blueGrey-200')
                }
            },
            shadowSize: 0
        };

        $('#barstacked-flotchart').plot(barStackeData, barStackedOptions);
    });

    // SPLINE
    // -----------------------------------
    $.get('server/chart/spline.json', function(data) {

        var splineData = data;
        var splineOptions = {
            series: {
                lines: {
                    show: false
                },
                points: {
                    show: true,
                    radius: 2
                },
                splines: {
                    show: true,
                    tension: 0.4,
                    lineWidth: 1,
                    fill: 1
                }
            },
            grid: {
                borderColor: 'rgba(162,162,162,.26)',
                borderWidth: 1,
                hoverable: true,
                backgroundColor: 'transparent'
            },
            tooltip: true,
            tooltipOpts: {
                content: function(label, x, y) {
                    return x + ' : ' + y;
                }
            },
            xaxis: {
                tickColor: 'rgba(162,162,162,.26)',
                font: {
                    color: Colors.byName('blueGrey-200')
                },
                mode: 'categories'
            },
            yaxis: {
                min: 0,
                max: 150, // optional: use it for a clear represetation
                tickColor: 'rgba(162,162,162,.26)',
                font: {
                    color: Colors.byName('blueGrey-200')
                },
                // position: (isRTL ? 'right' : 'left'),
                tickFormatter: function(v) {
                    return v /* + ' visitors'*/ ;
                }
            },
            shadowSize: 0
        };

        $('#spline-flotchart').plot(splineData, splineOptions);
    });

    // AREA
    // -----------------------------------
    $.get('server/chart/area.json', function(data) {
        var areaData = data;
        var areaOptions = {
            series: {
                lines: {
                    show: true,
                    fill: true,
                    fillColor: {
                        colors: [{
                            opacity: 0.5
                        }, {
                            opacity: 0.9
                        }]
                    }
                },
                points: {
                    show: false
                }
            },
            grid: {
                borderColor: 'rgba(162,162,162,.26)',
                borderWidth: 1,
                hoverable: true,
                backgroundColor: 'transparent'
            },
            tooltip: true,
            tooltipOpts: {
                content: function(label, x, y) {
                    return x + ' : ' + y;
                }
            },
            xaxis: {
                tickColor: 'rgba(162,162,162,.26)',
                font: {
                    color: Colors.byName('blueGrey-200')
                },
                mode: 'categories'
            },
            yaxis: {
                min: 0,
                max: 150,
                tickColor: 'rgba(162,162,162,.26)',
                font: {
                    color: Colors.byName('blueGrey-200')
                },
                // position: (isRTL ? 'right' : 'left')
            },
            shadowSize: 0
        };

        $('#area-flotchart').plot(areaData, areaOptions);

    });

    // LINE
    // -----------------------------------
    $.get('server/chart/line.json', function(data) {

        var lineData = data;
        var lineOptions = {
            series: {
                lines: {
                    show: true,
                    fill: 0.01
                },
                points: {
                    show: true,
                    radius: 4
                }
            },
            grid: {
                borderColor: 'rgba(162,162,162,.26)',
                borderWidth: 1,
                hoverable: true,
                backgroundColor: 'transparent'
            },
            tooltip: true,
            tooltipOpts: {
                content: function(label, x, y) {
                    return x + ' : ' + y;
                }
            },
            xaxis: {
                tickColor: 'rgba(162,162,162,.26)',
                font: {
                    color: Colors.byName('blueGrey-200')
                },
                mode: 'categories'
            },
            yaxis: {
                // position: (isRTL ? 'right' : 'left'),
                tickColor: 'rgba(162,162,162,.26)',
                font: {
                    color: Colors.byName('blueGrey-200')
                }
            },
            shadowSize: 0
        };

        $('#line-flotchart').plot(lineData, lineOptions);
    });

    // PIE
    // -----------------------------------
    var pieData = [{
        'label': 'CSS',
        'color': '#009688',
        'data': 40
    }, {
        'label': 'LESS',
        'color': '#FFC107',
        'data': 90
    }, {
        'label': 'SASS',
        'color': '#FF7043',
        'data': 75
    }];
    var pieOptions = {
        series: {
            pie: {
                show: true,
                innerRadius: 0,
                label: {
                    show: true,
                    radius: 0.8,
                    formatter: function(label, series) {
                        return '<div class="flot-pie-label">' +
                            //label + ' : ' +
                            Math.round(series.percent) +
                            '%</div>';
                    },
                    background: {
                        opacity: 0.8,
                        color: '#222'
                    }
                }
            }
        }
    };

    $('#pie-flotchart').plot(pieData, pieOptions);

    // DONUT
    // -----------------------------------
    var donutData = [{
        'color': '#4CAF50',
        'data': 60,
        'label': 'Coffee'
    }, {
        'color': '#009688',
        'data': 90,
        'label': 'CSS'
    }, {
        'color': '#FFC107',
        'data': 50,
        'label': 'LESS'
    }, {
        'color': '#FF7043',
        'data': 80,
        'label': 'Jade'
    }, {
        'color': '#3949AB',
        'data': 116,
        'label': 'AngularJS'
    }];
    var donutOptions = {
        series: {
            pie: {
                show: true,
                innerRadius: 0.5 // This makes the donut shape
            }
        }
    };

    $('#donut-flotchart').plot(donutData, donutOptions);

    // REALTIME
    // -----------------------------------
    var realTimeOptions = {
        series: {
            lines: {
                show: true,
                fill: true,
                fillColor: {
                    colors: ['#3F51B5', '#3F51B5']
                }
            },
            shadowSize: 0 // Drawing is faster without shadows
        },
        grid: {
            show: false,
            borderWidth: 0,
            minBorderMargin: 20,
            labelMargin: 10
        },
        xaxis: {
            tickFormatter: function() {
                return '';
            }
        },
        yaxis: {
            min: 0,
            max: 110
        },
        legend: {
            show: true
        },
        colors: ['#3F51B5']
    };

    // Generate random data for realtime demo
    var data = [],
        totalPoints = 300;

    var realTimeData = getRandomData();
    update();

    function getRandomData() {
        if (data.length > 0)
            data = data.slice(1);
        // Do a random walk
        while (data.length < totalPoints) {
            var prev = data.length > 0 ? data[data.length - 1] : 50,
                y = prev + Math.random() * 10 - 5;
            if (y < 0) {
                y = 0;
            } else if (y > 100) {
                y = 100;
            }
            data.push(y);
        }
        // Zip the generated y values with the x values
        var res = [];
        for (var i = 0; i < data.length; ++i) {
            res.push([i, data[i]]);
        }
        return [res];
    }

    function update() {
        realTimeData = getRandomData();
        $('#realtime-flotchart').plot(realTimeData, realTimeOptions);
        setTimeout(update, 30);
    }
    // end random data generation

}

export default FlotCharts;
