import Datamap from 'datamaps';

function dataMaps() {

    var elMapBasic = document.getElementById('datamap-basic');
    var elMapArc = document.getElementById('datamap-arc');

    if (!elMapBasic || !elMapArc) return;

    var mapBasic = new Datamap({
        element: elMapBasic,
        scope: 'usa',
        responsive: true,
        options: {
            width: 1110,
            legendHeight: 60 // optionally set the padding for the legend
        },
        geographyConfig: {
            highlightFillColor: Colors.byName('info'),
            highlightBorderWidth: 0
        },
        fills: {
            'HIGH': Colors.byName('info'),
            'MEDIUM': Colors.byName('info'),
            'LOW': Colors.byName('info'),
            'defaultFill': Colors.byName('gray-lighter')
        },
        data: {
            "AZ": {
                "fillKey": "MEDIUM",
            },
            "CO": {
                "fillKey": "HIGH",
            },
            "DE": {
                "fillKey": "LOW",
            },
            "GA": {
                "fillKey": "MEDIUM",
            }
        }
    });


    var mapArc = new Datamap({
        element: elMapArc,
        scope: 'usa',
        responsive: true,
        fills: {
            defaultFill: Colors.byName('blue-500'),
            win: Colors.byName('blueGrey-700'),
        },
        geographyConfig: {
            borderWidth: 0,
            highlightFillColor: Colors.byName('blue-100'),
            highlightBorderWidth: 0
        },
        data: {
            'TX': {
                fillKey: 'win'
            },
            'FL': {
                fillKey: 'win'
            },
            'NC': {
                fillKey: 'win'
            },
            'CA': {
                fillKey: 'win'
            },
            'NY': {
                fillKey: 'win'
            },
            'CO': {
                fillKey: 'win'
            }
        }
    })

    mapArc.arc([{
        origin: 'CA',
        destination: 'TX',
        options: {
            strokeWidth: 3,
            strokeColor: '#fff',
        }
    }, {
        origin: 'OR',
        destination: 'TX',
        options: {
            strokeWidth: 3,
            strokeColor: '#fff',
        }
    }, {
        origin: 'NY',
        destination: 'TX',
        options: {
            strokeWidth: 3,
            strokeColor: '#fff',
        }
    }]);

    // Allow resize by hooking window resize event
    $(window).on('resize.datamaps', function() {
        mapArc.resize();
        mapBasic.resize();
    });

}

export default dataMaps;
