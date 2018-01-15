function RickshawCharts() {

    if( !document.querySelector('#rickshaw-chart1') ||
        !document.querySelector('#rickshaw-chart2') ||
        !document.querySelector('#rickshaw-chart3') )
        return;

    var seriesData = [
        [],
        [],
        []
    ];
    var random = new Rickshaw.Fixtures.RandomData(150);

    for (var i = 0; i < 150; i++) {
        random.addData(seriesData);
    }
    // Big area chart
    var graph1 = new Rickshaw.Graph({
        element: document.querySelector('#rickshaw-chart1'),
        renderer: 'area',
        series: [{
            color: Colors.byName('indigo-700'),
            data: seriesData[0],
            name: 'New York'
        }, {
            color: Colors.byName('primary'),
            data: seriesData[1],
            name: 'London'
        }, {
            color: Colors.byName('info'),
            data: seriesData[2],
            name: 'Tokyo'
        }]
    });
    graph1.render();

    // Bar chart
    var graph2 = new Rickshaw.Graph({
        element: document.querySelector('#rickshaw-chart2'),
        renderer: 'bar',
        series: [{
            color: Colors.byName('green-700'),
            data: seriesData[0],
            name: 'New York'
        }, {
            color: Colors.byName('green-500'),
            data: seriesData[1],
            name: 'London'
        }, {
            color: Colors.byName('green-200'),
            data: seriesData[2],
            name: 'Tokyo'
        }]
    });
    graph2.render();

    // Scatterplot

    var seriesData2 = [
        [],
        [],
        []
    ];
    var random2 = new Rickshaw.Fixtures.RandomData(150);

    for (var j = 0; j < 200; j++) {
        random2.addData(seriesData2);
    }
    var graph3 = new Rickshaw.Graph({
        element: document.querySelector('#rickshaw-chart3'),
        width: '100%',
        renderer: 'scatterplot',
        legend: {
            toggle: true,
            highlight: true
        },
        series: [{
            color: Colors.byName('pink-700'),
            data: seriesData2[0],
            name: 'New York'
        }, {
            color: Colors.byName('pink-500'),
            data: seriesData2[1],
            name: 'London'
        }, {
            color: Colors.byName('pink-200'),
            data: seriesData2[2],
            name: 'Tokyo'
        }]
    });
    new Rickshaw.Graph.HoverDetail({
        graph: graph3,
        xFormatter: function(x) {
            return 't=' + x;
        },
        yFormatter: function(y) {
            return '$' + y;
        }
    });
    graph3.render();

    // Fluid charts
    // ---------------

    window.addEventListener('resize', function(){
        //- 1
        graph1.configure({
            width: $('#rickshaw-chart1').width(),
            height: $('#rickshaw-chart1').height()
        });
        graph1.render();
        //- 2
        graph2.configure({
            width: $('#rickshaw-chart2').width(),
            height: $('#rickshaw-chart2').height()
        });
        graph2.render();
        //- 3
        graph3.configure({
            width: $('#rickshaw-chart3').width(),
            height: $('#rickshaw-chart3').height()
        });
        graph3.render();

    });

}

export default RickshawCharts;
