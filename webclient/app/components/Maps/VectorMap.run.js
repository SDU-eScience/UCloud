function vectorMap() {

    if (document.getElementById('vector-map-world')) {

        var world_markers = [
            {'latLng': [47.14, 9.52],    'name': 'Liechtenstein'},
            {'latLng': [7.11, 171.06],   'name': 'Marshall Islands'},
            {'latLng': [17.3, -62.73],   'name': 'Saint Kitts and Nevis'},
            {'latLng': [3.2, 73.22],     'name': 'Maldives'},
            {'latLng': [35.88, 14.5],    'name': 'Malta'},
            {'latLng': [12.05, -61.75],  'name': 'Grenada'},
            {'latLng': [13.16, -61.23],  'name': 'Saint Vincent and the Grenadines'},
            {'latLng': [13.16, -59.55],  'name': 'Barbados'},
            {'latLng': [17.11, -61.85],  'name': 'Antigua and Barbuda'},
            {'latLng': [-4.61, 55.45],   'name': 'Seychelles'},
            {'latLng': [7.35, 134.46],   'name': 'Palau'},
            {'latLng': [42.5, 1.51],     'name': 'Andorra'}
        ];

        var world_options = {
            map: 'world_mill_en',
            normalizeFunction: 'polynomial',
            backgroundColor: '#fff',
            regionsSelectable: true,
            markersSelectable: true,
            markers: world_markers,
            regionStyle: {
                initial: {
                    fill: Colors.byName('gray-lighter')
                },
                hover: {
                    fill: Colors.byName('indigo-500'),
                    stroke: '#fff'
                },
            },
            markerStyle: {
                initial: {
                    fill: Colors.byName('pink-500'),
                    stroke: '#fff',
                    r: 10
                },
                hover: {
                    fill: Colors.byName('amber-500'),
                    stroke: '#fff'
                }
            }
        };

        // var series = {};

        $('#vector-map-world').vectorMap(world_options);
    }// world


        // USA Map
    if (document.getElementById('vector-map-usa')) {

        var usa_markers = [{
            latLng: [40.71, -74.00],
            name: 'New York'
        }, {
            latLng: [34.05, -118.24],
            name: 'Los Angeles'
        }, {
            latLng: [41.87, -87.62],
            name: 'Chicago'
        }, {
            latLng: [29.76, -95.36],
            name: 'Houston'
        }, {
            latLng: [39.95, -75.16],
            name: 'Philadelphia'
        }, {
            latLng: [38.90, -77.03],
            name: 'Washington'
        }, {
            latLng: [37.36, -122.03],
            name: 'Silicon Valley',
            style: {
                fill: Colors.byName('green-500'),
                r: 20
            }
        }];

        var usa_options = {
            map: 'us_mill_en',
            normalizeFunction: 'polynomial',
            backgroundColor: '#fff',
            regionsSelectable: true,
            markersSelectable: true,
            markers: usa_markers,
            regionStyle: {
                initial: {
                    fill: Colors.byName('deepPurple-400')
                },
                hover: {
                    fill: Colors.byName('deepPurple-100'),
                    stroke: '#fff'
                },
            },
            markerStyle: {
                initial: {
                    fill: Colors.byName('amber-500'),
                    stroke: '#fff',
                    r: 10
                },
                hover: {
                    fill: Colors.byName('orange-500'),
                    stroke: '#fff'
                }
            }
        };

        $('#vector-map-usa').vectorMap(usa_options);

    }

}

export default vectorMap;
