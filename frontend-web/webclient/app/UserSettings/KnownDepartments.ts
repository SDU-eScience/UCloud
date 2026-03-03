import type {KnownDepartmentsMap} from "@/UserSettings/types";

/* This is a map of Danish Universities / Faculties / Departments 
Other organizational sub-units like centers or sections are normally not included here, but 
there are some exceptions as noted below. We try to include all sensible options needed so 
that users can map 1-2-1 to the list below. */

const knownDepartments: KnownDepartmentsMap = {
    "aau.dk": [
        {
            "faculty": "University Administrative Services"
        },
        {
            "faculty": "Faculty of Social Sciences and Humanities",
            "departments": [
                "Administration",
                "AAU Business School",
                "Department of Culture and Communication",
                "Department of Society and Politics",
                "Department of Law"
            ]
        },
        {
            "faculty": "Faculty of Engineering and Science",
            "departments": [
                "Administration",
                "Department of the Built Environment",
                "Department of Chemistry and Bioscience",
                "Department of Energy",
                "Department of Materials and Production",
                "Department of Mathematical Sciences"
            ]
        },
        {
            "faculty": "Faculty of Medicine",
            "departments": [
                "Administration",
                "Department of Health Science and Technology",
                "Department of Clinical Medicine"
            ]
        },
        {
            "faculty": "Technical Faculty of IT and Design",
            "departments": [
                "Administration",
                "Department of Computer Science",
                "Department of Electronic Systems",
                "Department of Architecture, Design and Media Technology",
                "Department of Sustainability and Planning"
            ]
        }
    ],
    "au.dk": [
        {
            "faculty": "University Administrative Services"
        },
        {
            /* the faculty of arts is divided into schools each of which has several departments */
            "faculty": "The Faculty of Arts",
            "departments": [
                "Administration",
                "Danish school of Education/Department of Education studies",
                "Danish school of Education/Department of Educational anthropology and Educational psychology",
                "Danish school of Education/Department of Educational sociology",
                "Danish school of Education/Department of Education studies",
                "Danish school of Education",
                "School of Communication and Culture/Department of Art History, Aesthetics & Culture and Museology",
                "School of Communication and Culture/Department of Comparative Literature and Rhetoric",
                "School of Communication and Culture/Department of Digital Design and Information Studies",
                "School of Communication and Culture/Department of English",
                "School of Communication and Culture/Department of German and Romance Languages",
                "School of Communication and Culture/Department of Linguistics, Cognitive Science and Semiotics",
                "School of Communication and Culture/Department of Media and Journalism Studies",
                "School of Communication and Culture/Department of Scandinavian Studies and Experience Economy",
                "School of Culture and Society/Department of Anthropology",
                "School of Culture and Society/Department of Archaeology",
                "School of Culture and Society/Department of Global Studies",
                "School of Culture and Society/Department of History and Classical Studies",
                "School of Culture and Society/Department of Philosophy and History of Ideas",
                "School of Culture and Society/Department of Theology",
                "School of Culture and Society/Department of The Study of Religion"
            ]
        },
        {
            "faculty": "Aarhus BSS",
            "departments": [
                "Administration",
                "Department of Business Development and Technology",
                "Department of Economics and Business Economics",
                "Department of Management",
                "Department of Political Science",
                "Department of Law",
                "Department of Psychology and Behavioural Sciences"
            ]
        },
        {
            "faculty": "The Faculty of Health",
            "departments": [
                "Administration",
                "Department of Clinical Medicine",
                "Department of Biomedicine",
                "Department of Dentistry and Oral Health",
                "Department of Public Health",
                "Department of Forensic Medicine"
            ]
        },
        {
            "faculty": "The Faculty of Natural Sciences",
            "departments": [
                "Administration",
                "Department of Biology",
                "Department of Computer Science",
                "Department of Physics and Astronomy",
                "Department of Geoscience",
                "Department of Chemistry",
                "Department of Mathematics",
                "Department of Molecular Biology and Genetics",
                "Department of Interdisciplinary Nanoscience Center"
            ]
        },
        {
            "faculty": "The Faculty of Technical Sciences",
            "departments": [
                "Administration",
                "Department of Agroecology",
                "Department of Biological and Chemical Engineering",
                "Department of Civil and Architectural Engineering",
                "Department of Ecoscience",
                "Department of Electrical and Computer Engineering",
                "Department of Food Science",
                "Department of Animal and Veterinary Sciences",
                "Department of Mechanical and Production Engineering",
                "Department of Environmental Science",
                /* normally we don't include centers here, but these are special to the organization of the faculty */ 
                "DCE - Danish Centre For Environment And Energy",
                "DCA - Danish Centre For Food And Agriculture",
                "Center for Quantitative Genetics and Genomics"
            ]
        }
    ],
    /* CBS does not have faculties but departments. The faculty field corresponds to the department. */
    "cbs.dk": [
        {
            "faculty": "University Administrative Services"
        },
        {
            "faculty": "Department of Accounting (ACC)"
        },
        {
            "faculty": "Department of Business Humanities and Law (BHL)"
        },
        {
            "faculty": "Department of Digitalization (DIGI)"
        },
        {
            "faculty": "Department of Economics (ECON)"
        },
        {
            "faculty": "Department of Finance (FI)"
        },
        {
            "faculty": "Department of International Economics, Government and Business (EGB)"
        },
        {
            "faculty": "Department of Management, Society and Communication (MSC)"
        },
        {
            "faculty": "Department of Marketing (MARKTG)"
        },
        {
            "faculty": "Department of Operations Management (OM)"
        },
        {
            "faculty": "Department of Organization (IOA)"
        },
        {
            "faculty": "Department of Strategy and Innovation (SI)"
        }
    ],
    /* DTU does not have faculties but departments and university centers. The faculty field corresponds 
    to those. Where relevant, the department field correspond to the research sections (for DTU 
    management are called "divisions").
    */
    "dtu.dk": [
        {
            "faculty": "University Administrative Services"
        },
        {
            "faculty": "DTU Aqua"
        },
        {
            "faculty": "DTU Bioengineering"
        },
        {
            "faculty": "DTU Biosustain"
        },
        {
            "faculty": "DTU Chemical Engineering"
        },
        {
            "faculty": "DTU Chemistry"
        },
        {
            "faculty": "DTU Compute",
            "departments": [
                "Administration",
                "Algorithms, Logic and Graphs",
                "Cognitive Systems",
                "Cybersecurity Engineering",
                "Dynamical Systems",
                "Embedded Systems Engineering",
                "Mathematics",
                "Scientific Computing",
                "Software Systems Engineering",
                "Statistics and Data Analysis",
                "Visual Computing",
                "Innovation",
                "Tech4Civ"
            ]
        },
        {
            "faculty": "DTU Construct"
        },
        {
            "faculty": "DTU Electro"
        },
        {
            "faculty": "DTU Energy",
            "departments": [
                "Administration",
                "Applied Ceramics and Processing",
                "Atomic Scale Materials Modelling",
                "Autonomous Materials Discovery",
                "Electrochemical Materials",
                "Functional Oxides",
                "Solid State Electrochemistry",
                "Structural Analysis and Modelling"
            ]
        },
        {
            "faculty": "DTU Engineering Technology",
            "departments": [
                "Administration",
                "Building Technology and Processes",
                "Business Development",
                "Energy Technology and Computer Science",
                "Engineering Education Research",
                "Innovation Processes and Entrepreneurship",
                "Mechanical Technology",
                "Production, Transportation and Planning",
                "Strategy and Leadership Development"
            ]
        },
        {
            "faculty": "DTU Entrepreneurship"
        },
        {
            "faculty": "DTU Food"
        },
        {
            "faculty": "DTU Health Tech",
            "departments": [
                "Administration",
                "Bioinformatics",
                "Cell and Drug Technologies",
                "Digital Health",
                "Drug Delivery and Sensing",
                "Experimental and Translational Immunology",
                "Hearing Systems",
                "Magnetic Resonance",
                "Medical Isotopes and Dosimetry",
                "Optical Sensing and Imaging Systems",
                "Ultrasound and Biomechanics"
            ]
        },
        {
            "faculty": "DTU Learn for Life"
        },
        {
            "faculty": "DTU Management",
            "departments": [
                "Administration",
                "Management Science",
                "Climate and Energy Policy",
                "Technology and Business Studies",
                "Transportation Science"
            ]
        },
        {
            "faculty": "DTU Nanolab"
        },
        {
            "faculty": "DTU Offshore"
        },
        {
            "faculty": "DTU Physics",
            "departments": [
                "Administration",
                "Surface Physics & Catalysis",
                "Computational Atomic-scale Materials Design",
                "2D Materials Engineering and Physics",
                "Magnetic Materials and Neturon Scattering",
                "Materials Physics in Time and Space",
                "Plasma Physics and Fusion Energy",
                "Biophysiscs and Fluids",
                "Luminescence Physics and Technologies",
                "Quantum Physics and Information Technology",
                /* normally we don't include centers here, but these are special to the organization of the faculty */ 
                "Centre for Nuclear Energy Technology",
                "Center for Visualizing Catalytic Processes"
            ]
        },
        {
            "faculty": "DTU Skylab"
        },
        {
            "faculty": "DTU Space",
            "departments": [
                "Administration",
                "Astrophysics and Atmospheric Physics",
                "Geodesy and Earth Observation",
                "Geomagnetism and Geospace",
                "Microwaves and Remote Sensing",
                "Measurement and Instrumentation Systems",
                "Electromagnetic systems",
                /* normally we don't include centers here, but these are special to the organization of the faculty */ 
                "ESA BIC Denmark",
                "DTU Space Drone Center"
            ]
        },
        {
            "faculty": "DTU Sustain",
            "departments": [
                "Administration",
                "Environmental Contamination & Chemicals",
                "Geotechnics and Geology",
                "Indoor Environment",
                "Construction Materials & Durability",
                "Quantitative Sustainability Assessment",
                "Waste, Climate & Monitoring",
                "Water Systems",
                "Water Technology and Processes"
            ]
        },
        {
            "faculty": "DTU Wind",
            "departments": [
                "Administration",
                "Wind Energy Materials and Components",
                "Wind Turbine Design",
                "Wind Energy Systems",
                "Power and Energy Systems"
            ]
        }
    ],
    /* ITU does not have faculties but "research units'". The faculty field corresponds to those. */
    "itu.dk": [
        {
            "faculty": "University Administrative Services"
        },
        {
            "faculty": "Data Science"
        },
        {
            "faculty": "Data, Systems, and Robotics"
        },
        {
            "faculty": "Digital Business Innovation"
        },
        {
            "faculty": "Digitalization, Democracy, and Governance"
        },
        {
            "faculty": "Human-Computer Interaction and Design"
        },
        {
            "faculty": "Play, Culture, and AI"
        },
        {
            "faculty": "Software Engineering"
        },
        {
            "faculty": "Technologies in Practice"
        },
        {
            "faculty": "Theoretical Computer Science"
        }
    ],
    "kb.dk": "freetext",
    "kp.dk": "freetext",
    "ku.dk": [
        {
            "faculty": "University Administrative Services"
        },
        {
            "faculty": "Faculty of Health and Medical Sciences",
            "departments": [
                "Administration",
                "Department of Biomedical Sciences",
                "Department of Cellular and Molecular Medicine",
                "Department of Immunology and Microbiology",
                "Department of Neuroscience",
                "Department of Forensic Medicine",
                "Department of Public Health",
                "Department of Odontology",
                "Department of Clinical Medicine",
                "Department of Pharmacy",
                "Department of Drug Design and Pharmacology",
                "Department of Veterinary Clinical Sciences",
                "Department of Veterinary and Animal Sciences",
                "Globe Institute"
            ]
        },
        {
            "faculty": "Faculty of Humanities",
            "departments": [
                "Administration",
                "Department of Arts and Cultural Studies",
                "Department of Communication",
                "Department of Cross-Cultural and Regional Studies",
                "Department of English, Germanic and Romance Studies",
                "Department of Nordic Studies and Linguistics",
                "Saxo Institute",
            ]
        },
        {
            "faculty": "Faculty of Law"
        },
        {
            "faculty": "Faculty of Science",
            "departments": [
                "Administration",
                "Department of Biology",
                "Department of Chemistry",
                "Department of Computer Science",
                "Department of Food and Resource Economics",
                "Department of Food Science",
                "Department of Geosciences and Natural Resource Management",
                "Department of Mathematical Sciences",
                "Department of Nutrition, Exercise and Sports",
                "Department of Plant and Environmental Sciences",
                "Department of Science Education",
                "Natural History Museum of Denmark",
                "Niels Bohr Institute"
            ]
        },
        {
            "faculty": "Faculty of Social Sciences",
            "departments": [
                "Administration",
                "Department of Anthropology",
                "Department of Economics",
                "Department of Political Science",
                "Department of Psychology",
                "Department of Sociology"
            ]
        },
        {
            "faculty": "Faculty of Theology"
        }
    ],
    "regionh.dk": "freetext",
    /* RUC does not have faculties but departments. The faculty field corresponds to the department. */
    "ruc.dk": [
        {
            "faculty": "University Administrative Services"
        },
        {
            "faculty": "Communication and Arts"
        },
        {
            "faculty": "People and Technology"
        },
        {
            "faculty": "Science and Environment"
        },
        {
            "faculty": "Social Sciences and Business "
        }
    ],
    "sdu.dk": [
        {
            "faculty": "University Administrative Services"
        },
        {
            "faculty": "Faculty of Humanities",
            "departments": [
                "Administration",
                "Department of Culture and Language",
                "Department of Design, Media and Educational Science",
            ]
        },
        {
            "faculty": "Faculty of Science",
            "departments": [
                "Administration",
                "Department of Biochemistry and Molecular Biology",
                "Department of Biology",
                "Department of Mathematics and Computer Science",
                "Department of Physics, Chemistry and Pharmacy",
            ]
        },
        {
            "faculty": "Faculty of Business and Social Sciences",
            "departments": [
                "Administration",
                "Department of Business & Management",
                "Department of Business and Sustainability",
                "Department of Economics",
                "Department of Law",
                "Department of Political Science and Public Management",
            ]
        },
        {
            "faculty": "Faculty of Health Sciences",
            "departments": [
                "Administration",
                "Department of Clinical Research",
                "Department of Forensic Medicine",
                "Department of Molecular Medicine",
                "Department of Psychology",
                "Department of Public Health",
                "Department of Sports Science and Clinical Biomechanics",
                "IRS - Department of Regional Health Research",
                "National Institute of Public Health"
            ]
        },
        {
            "faculty": "Faculty of Engineering",
            "departments": [
                "Administration",
                "Department of Green Technology",
                "Department of Technology and Innovation",
                "Institute of Mechanical and Electrical Engineering",
                "Mads Clausen Institute",
                "The Maersk Mc-Kinney Moller Institute"
            ]
        }
    ],
    "setur.fo": [
        {
            "faculty": "University Administrative Services"
        },
        {
            "faculty": "Faculty of Faroese Language and Literature",
        },
        {
            "faculty": "Faculty of Education"
        },
        {
            "faculty": "Faculty of History and Social Sciences"
        },
        {
            "faculty": "Faculty of Science and Technology"
        },
        {
            "faculty": "Faculty of Health Sciences"
        }
    ],
    "ucsyd.dk": "freetext",
    "ucl.dk": "freetext",
    "viauc.dk": "freetext"
};

export default knownDepartments;
