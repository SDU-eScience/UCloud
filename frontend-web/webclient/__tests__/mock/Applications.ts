import * as UCloud from "../../app/UCloud";

export const applicationsPage: UCloud.Page<UCloud.compute.ApplicationSummaryWithFavorite> = {
    itemsInTotal: 45,
    itemsPerPage: 100,
    pageNumber: 0,
    items: [
        {
            metadata: {
                name: "ansaws",
                version: "2020",
                authors: [
                    "ansaws Inc."
                ],
                title: "ansaws",
                description: "ANSYS is a general nonlinear multiphysics software offering structural and  thermodynamic analysis, continuum flow analysis, analysis of electrostatic and  electromagnetic fields and acoustic analysis.\n",
                public: true
            },
            favorite: false,
            tags: [
                "Featured",
                "Engineering"
            ]
        },
        {
            metadata: {
                name: "ays-el",
                version: "2020.R2-1",
                authors: [
                    "Ays Inc."
                ],
                title: "AYS E-Desktop",
                description: "Ansys Electronics Desktop is a comprehensive platform that enables electrical engineers to design and simulate various electrical,  electronic and electromagnetic components, devices and systems. \n",
                public: false
            },
            favorite: false,
            tags: [
                "Featured",
                "Engineering"
            ]
        },
        {
            metadata: {
                name: "centos-xfce",
                version: "7",
                authors: [
                    "Foo Bar <foo@bar.dk>"
                ],
                title: "CentOS Xfce",
                description: "CentOS Xfce virtual desktop environment.\n",
                website: "https://docs.cloud.sdu.dk/Apps/centos.html",
                public: true
            },
            favorite: false,
            tags: [
                "Featured"
            ]
        },
        {
            metadata: {
                name: "coder",
                version: "1.48.2",
                authors: [
                    "coder.com"
                ],
                title: "Coder",
                description: "Run Visual Studio Code on UCloud and access it through your browser.  For more information, check [here](https://coder.com).\n",
                website: "https://docs.cloud.sdu.dk/Apps/coder.html",
                public: false
            },
            favorite: false,
            tags: [
                "Featured",
                "Development"
            ]
        },
        {
            metadata: {
                name: "codr-cpp",
                version: "1.48.2",
                authors: [
                    "codr.com"
                ],
                title: "Codr C++",
                description: "Run Visual Studio code on UCloud and access it through your browser.\n",
                website: "https://docs.cloud.sdu.dk/Apps/coder.html",
                public: false
            },
            favorite: false,
            tags: [
                "Development",
                "Featured"
            ]
        },
        {
            "metadata": {
                name: "codr-cuda",
                version: "1.48.2-1",
                authors: [
                    "codr.com"
                ],
                title: "Codr CUDA",
                description: "Run Visual Studio code on UCloud and access it through your browser.\n",
                website: "https://docs.cloud.sdu.dk/Apps/coder.html",
                public: false
            },
            favorite: false,
            tags: [
                "Development",
                "Featured"
            ]
        },
        {
            metadata: {
                name: "codr-cuda",
                version: "1.45.1",
                authors: [
                    "codr.com"
                ],
                title: "Coder Cuda",
                description: "Run Visual Studio code on UCloud and access it through your browser.\n",
                website: "https://docs.cloud.sdu.dk/Apps/coder.html",
                public: false
            },
            favorite: false,
            tags: [
                "Development",
                "Featured"
            ]
        },
        {
            metadata: {
                name: "codr-python",
                version: "1.48.2",
                authors: [
                    "codr.com"
                ],
                title: "Codr Python",
                description: "Run Visual Studio code on UCloud and access it through your browser.\n",
                website: "https://docs.cloud.sdu.dk/Apps/coder.html",
                public: false
            },
            favorite: false,
            tags: [
                "Development",
                "Featured"
            ]
        },
        {
            metadata: {
                name: "cmsl",
                version: "5.4-5",
                authors: [
                    "CMSL Inc."
                ],
                title: "CMSL",
                description: "CMSL Multiphysics is a cross-platform finite element analysis, solver and multiphysics  simulation software. It allows conventional physics-based user interfaces and  coupled systems of partial differential equations.  COMSOL provides an IDE and unified workflow for electrical, mechanical, fluid, acoustics and  chemical applications.\n",
                public: true
            },
            favorite: false,
            tags: [
                "Featured",
                "Engineering",
                "Applied Science"
            ]
        },
        {
            "metadata": {
                "name": "cosmomc-te",
                "version": "May2020-3",
                "authors": [
                    "Antony Lewis",
                    "Sarah Bridle"
                ],
                "title": "CosmoMC",
                "description": "[CosmoMC](https://cosmologist.info/cosmomc/) is a Fortran 2008 Markov-Chain Monte-Carlo engine for exploring cosmological parameter space,  together with Fortran and Python code for analysing Monte-Carlo samples and importance sampling.\n",
                "website": "https://docs.cloud.sdu.dk/Apps/cosmo-mc.html",
                "public": false
            },
            "favorite": false,
            "tags": [
                "Featured",
                "Natural Science"
            ]
        },
        {
            "metadata": {
                "name": "dash",
                "version": "1.8.0",
                "authors": [
                    "Plotly"
                ],
                "title": "Dash",
                "description": "Dash is a productive Python framework for building web applications.\n",
                "website": "https://docs.cloud.sdu.dk/Apps/dash.html",
                "public": true
            },
            "favorite": false,
            "tags": [
                "Featured",
                "Data Analytics"
            ]
        },
        {
            "metadata": {
                "name": "fenics",
                "version": "2019.1.0.r3-2",
                "authors": [
                    "Jack S. Hale <jack.hale@uni.lu>",
                    "Federica Lo Verso <federica@imada.sdu.dk>"
                ],
                "title": "FEniCS",
                "description": "The FEniCS Project is a collection of free and open-source software components with the common goal to enable automated solution of differential equations. The components provide scientific computing tools for working with computational meshes, finite-element variational formulations of ordinary and partial differential equations, and numerical linear algebra with the high-level Python and C++ interfaces.\n",
                "public": true
            },
            "favorite": false,
            "tags": [
                "Applied Science",
                "Featured"
            ]
        },
        {
            "metadata": {
                "name": "figlet",
                "version": "1.0.0",
                "authors": [
                    "Dan Sebastian Thrane <dthrane@imada.sdu.dk>"
                ],
                "title": "Figlet",
                "description": "Render some text with Figlet Docker!\n",
                "public": true
            },
            "favorite": false,
            "tags": [
                "Featured"
            ]
        },
        {
            "metadata": {
                "name": "freecad",
                "version": "0.18.4-1",
                "authors": [
                    "Federica Lo Verso <federica@imada.sdu.dk>"
                ],
                "title": "FreeCAD",
                "description": "FreeCAD is a general-purpose parametric 3D computer-aided design (CAD) modeler and a building information modeling (BIM) software with finite element method (FEM) support. FreeCAD is intended for mechanical engineering product design but also expands to architecture or electrical engineering. Users can extend the functionality of the software using the Python programming language. Meshing tools include Gmsh and Netgen.\n",
                "website": "https://docs.cloud.sdu.dk/Apps/freecad.html",
                "public": false
            },
            "favorite": false,
            "tags": [
                "Featured",
                "Applied Science"
            ]
        },
        {
            "metadata": {
                "name": "ggir",
                "version": "2.1.0-1",
                "authors": [
                    "Vincent van Hees"
                ],
                "title": "GGIR",
                "description": "A tool to process and analyse data collected with wearable raw acceleration sensors. \n",
                "public": true
            },
            "favorite": false,
            "tags": [
                "Featured",
                "Applied Science"
            ]
        },
        {
            "metadata": {
                "name": "gromacs-te",
                "version": "2020.2",
                "authors": [
                    "University of Groningen",
                    "Royal Institute of Technology",
                    "Upsala University"
                ],
                "title": "GROMACS",
                "description": "GROMACS is a molecular dynamics package mainly designed for simulations of proteins, lipids, and nucleic acids.\n",
                "website": "https://docs.cloud.sdu.dk/Apps/gromacs.html",
                "public": false
            },
            "favorite": false,
            "tags": [
                "Natural Science",
                "Featured"
            ]
        },
        {
            "metadata": {
                "name": "jupyter-all-spark",
                "version": "2.2.5",
                "authors": [
                    "Emiliano Molinaro <molinaro@imada.sdu.dk>"
                ],
                "title": "JupyterLab",
                "description": "JupyterLab ecosystem for Data Science. Installed kernels:  Python, R, and Scala with support for Apache Spark,  Clojure, Go, Groovy, Java, Javascript, Julia, Kotlin, Octave, Ruby, Rust, SQL.\n",
                "website": "https://docs.cloud.sdu.dk/Apps/jupyter-lab.html",
                "public": true
            },
            "favorite": false,
            "tags": [
                "Featured",
                "Data Analytics",
                "Development"
            ]
        },
        {
            "metadata": {
                "name": "jupyter-latex",
                "version": "1.2.6",
                "authors": [
                    "Emiliano Molinaro <molinaro@imada.sdu.dk>"
                ],
                "title": "JupyterLab LaTeX",
                "description": "JupyterLab with TeX Live 2019 and LaTeX support.\n",
                "website": "https://docs.cloud.sdu.dk/Apps/jupyter-lab.html",
                "public": true
            },
            "favorite": false,
            "tags": [
                "Development",
                "Featured",
                "Data Analytics"
            ]
        },
        {
            "metadata": {
                "name": "knime",
                "version": "4.0.2-6",
                "authors": [
                    "KNIME Team"
                ],
                "title": "KNIME",
                "description": "KNIME Analytics Platform is an open source software for creating data science. KNIME integrates various components for machine learning and data mining  through its modular data pipelining concept. A graphical user interface  allows assembly of nodes blending different data sources,  including preprocessing (ETL), for modeling, data analysis and visualization without,  or with only minimal, programming.\n",
                "public": true
            },
            "favorite": false,
            "tags": [
                "Featured",
                "Data Analytics"
            ]
        },
        {
            "metadata": {
                "name": "kodningslab",
                "version": "0.1.0-dev",
                "authors": [
                    "Mads Due Kristensen"
                ],
                "title": "Kodningslab",
                "description": "Kodningslab app\n",
                "public": false
            },
            "favorite": false,
            "tags": [
                "Featured",
                "Social Science"
            ]
        },
        {
            "metadata": {
                "name": "kodningslab",
                "version": "2.1.2",
                "authors": [
                    "Lotte Kramer Schmidt <lkramer@health.sdu.dk>",
                    "Birgit Jensen <bijensen@health.sdu.dk>"
                ],
                "title": "Kodningslab.dk",
                "description": "Kodningslab.dk is an online platform where supervision and feedback is given to motivational conversations,  also known as conversations performed according to the method \"Motivational Interviewing\" (MI).\n",
                "public": false
            },
            "favorite": false,
            "tags": [
                "Featured",
                "Social Science"
            ]
        },
        {
            "metadata": {
                "name": "lammps",
                "version": "3Mar2020",
                "authors": [
                    "Sandia National Laboratories",
                    "Temple University"
                ],
                "title": "LAMMPS Molecular Dynamics Simulator",
                "description": "Large-scale Atomic/Molecular Massively Parallel Simulator (LAMMPS)  is a molecular dynamics program from Sandia National Laboratories. \n",
                "website": "https://docs.cloud.sdu.dk/Apps/lammps.html",
                "public": true
            },
            "favorite": false,
            "tags": [
                "Featured",
                "Applied Science"
            ]
        },
        {
            "metadata": {
                "name": "matlab",
                "version": "2020b",
                "authors": [
                    "MathWorks"
                ],
                "title": "MATLAB",
                "description": "MATLAB is a programming platform designed for engineers and scientists.  It combines a desktop environment tuned for iterative analysis and design processes  with a programming language that expresses matrix and array mathematics directly.\n",
                "website": "https://docs.cloud.sdu.dk/Apps/matlab.html",
                "public": false
            },
            "favorite": false,
            "tags": [
                "Data Analytics",
                "Featured"
            ]
        },
        {
            "metadata": {
                "name": "minio",
                "version": "2020-10-28",
                "authors": [
                    "MinIO, Inc."
                ],
                "title": "MinIO",
                "description": "MinIO is a high performance object storage server. \n      \n",
                "website": "https://docs.cloud.sdu.dk/Apps/minio.html",
                "public": true
            },
            "favorite": false,
            "tags": [
                "Featured",
                "Data Analytics"
            ]
        },
        {
            "metadata": {
                "name": "netlogo",
                "version": "6.1.1a",
                "authors": [
                    "Federica Lo Verso <federica@imada.sdu.dk>"
                ],
                "title": "NetLogo",
                "description": "NetLogo is a multi-agent programmable modeling environment used by students, teachers, and researchers worldwide. It also powers HubNet participatory simulations.  \n",
                "website": "https://docs.cloud.sdu.dk/Apps/netlogo.html",
                "public": false
            },
            "favorite": false,
            "tags": [
                "Featured",
                "Social Science"
            ]
        },
        {
            "metadata": {
                "name": "nextflow",
                "version": "20.01.0",
                "authors": [
                    "Paolo di Tommaso",
                    "Maria Chatzou",
                    "Evan W. Foldem",
                    "Pablo Prietro Barja",
                    "Emilio Palumbo",
                    "Cedric Notredame"
                ],
                "title": "Nextflow",
                "description": "Nextflow is a bioinformatics workflow manager that enables the development of portable and reproducible workflows.\n",
                "website": "https://docs.cloud.sdu.dk/Apps/nextflow.html",
                "public": true
            },
            "favorite": false,
            "tags": [
                "Featured",
                "Bioinformatics",
                "Data Analytics"
            ]
        },
        {
            "metadata": {
                "name": "openfoam",
                "version": "7",
                "authors": [
                    "Federica Lo Verso <federica@imada.sdu.dk>"
                ],
                "title": "openFOAM",
                "description": "OpenFOAM (Open-source Field Operation And Manipulation)  is a C++ toolbox for the development of customized numerical  solvers, and pre-/post-processing utilities for the solution  of continuum mechanics problems, including computational  fluid dynamics (CFD). The main post-processing tool provided  with OpenFOAM is a reader module to run with ParaView, a  visualization application.\n",
                "website": "https://docs.cloud.sdu.dk/Apps/openfoam.html",
                "public": true
            },
            "favorite": false,
            "tags": [
                "Applied Science",
                "Featured"
            ]
        },
        {
            "metadata": {
                "name": "otree",
                "version": "3.2.2",
                "authors": [
                    "Daniel Li Chen",
                    "Martin Walter Schonger",
                    "Chris Wickens"
                ],
                "title": "oTree",
                "description": "oTree is a framework based on Python for building controlled behavioral experiments in economics, psycology and related fields,  multiplayer strategy games, like  public goods game, and auctions and survey and quizzes\n",
                "website": "https://docs.cloud.sdu.dk/Apps/otree.html",
                "public": false
            },
            "favorite": false,
            "tags": [
                "Featured",
                "Social Science"
            ]
        },
        {
            "metadata": {
                "name": "overleaf",
                "version": "2.4.2",
                "authors": [
                    "John Hammersley",
                    "John Lees-Miller"
                ],
                "title": "Overleaf",
                "description": "Overleaf is a collaborative cloud-based LaTeX editor used for writing, editing and publishing scientific documents\n",
                "website": "https://docs.cloud.sdu.dk/Apps/overleaf.html",
                "public": false
            },
            "favorite": false,
            "tags": [
                "Development",
                "Featured"
            ]
        },
        {
            "metadata": {
                "name": "pytorch-te",
                "version": "1.7.0",
                "authors": [
                    "Adam Paszke",
                    "Sam Gross",
                    "Soumith Chintala",
                    "Gregory Chanan",
                    "NVIDIA Deeplearning & AI"
                ],
                "title": "PyTorch",
                "description": "PyTorch is a GPU accelerated tensor computational framework with a Python front end.  Functionality can be easily extended with common Python libraries such as NumPy, SciPy, and Cython.  Automatic differentiation is done with a tape-based system at both a functional and neural network layer level.\n",
                "website": "https://docs.cloud.sdu.dk/Apps/pytorch.html",
                "public": true
            },
            "favorite": false,
            "tags": [
                "Featured",
                "Development",
                "Data Analytics"
            ]
        },
        {
            "metadata": {
                "name": "quantumespresso",
                "version": "6.5",
                "authors": [
                    "Federica Lo Verso <federica@imada.sdu.dk>"
                ],
                "title": "quantumESPRESSO",
                "description": "QuantumESPRESSO is an integrated suite of Open-Source  computer codes for electronic-structure calculations and  materials modeling at the nanoscale. It is based on  density-functional theory, plane waves, and pseudopotentials.  XCrySDen, a crystalline and molecular structure visualisation  program, is also included.\n",
                "website": "https://docs.cloud.sdu.dk/Apps/quantumespresso.html",
                "public": true
            },
            "favorite": false,
            "tags": [
                "Applied Science",
                "Featured"
            ]
        },
        {
            "metadata": {
                "name": "ros",
                "version": "2.0-dashing",
                "authors": [
                    "Emiliano Molinaro <molinaro@imada.sdu.dk>"
                ],
                "title": "Robot Operating System",
                "description": "The Robot Operating System (ROS) is a set of software libraries and tools for building robot applications.  Installed distribution:    [ROS Melodic](http://wiki.ros.org/melodic).  Additional software libraries:  [Gazebo](http://gazebosim.org/),  [MoveIt](https://moveit.ros.org/) (Melodic), [Open Motion Planning Library](https://ompl.kavrakilab.org/) (OMPL), [MARA](https://github.com/AcutronicRobotics/MARA), [OpenAI Gym](https://gym.openai.com), [ROS2Learn](https://github.com/AcutronicRobotics/ros2learn).\n",
                "website": "https://docs.cloud.sdu.dk/Apps/ros2.html",
                "public": true
            },
            "favorite": false,
            "tags": [
                "Featured",
                "Engineering",
                "Applied Science"
            ]
        },
        {
            "metadata": {
                "name": "rstudio",
                "version": "3.6.3",
                "authors": [
                    "Emiliano Molinaro <molinaro@imada.sdu.dk>"
                ],
                "title": "RStudio",
                "description": "Run [RStudio server](https://rstudio.org) on UCloud.\n",
                "website": "https://docs.cloud.sdu.dk/Apps/rstudio.html",
                "public": true
            },
            "favorite": false,
            "tags": [
                "Development",
                "Featured",
                "Data Analytics"
            ]
        },
        {
            "metadata": {
                "name": "rstudio-am",
                "version": "3.6.2-5",
                "authors": [
                    "Emiliano Molinaro <molinaro@imada.sdu.dk>"
                ],
                "title": "RStudio Activity Monitor",
                "description": "[RStudio](https://rstudio.org) on UCloud with R-packages for GPS and accelerometer data analysis:  [PALMSplus](https://thets.github.io/palmsplusr/index.html), [GGIR](https://cran.r-project.org/web/packages/GGIR/vignettes/GGIR.html), [modeid](https://github.com/dprocter/modeid), [TLBC](http://ieng9.ucsd.edu/~kellis/TLBC.html).\n",
                "website": "https://docs.cloud.sdu.dk/Apps/rstudio.html",
                "public": true
            },
            "favorite": false,
            "tags": [
                "Featured",
                "Data Analytics"
            ]
        },
        {
            "metadata": {
                "name": "rstudio-genomics",
                "version": "3.6.2-5",
                "authors": [
                    "Emiliano Molinaro <molinaro@imada.sdu.dk>"
                ],
                "title": "RStudio Genomics",
                "description": "[RStudio](https://rstudio.org) with R-packages for Genomics data analysis:  [AnnotationHub](https://doi.org/doi:10.18129/B9.bioc.AnnotationHub), [annotationTools](https://doi.org/doi:10.18129/B9.bioc.annotationTools), [biomaRt](https://doi.org/doi:10.18129/B9.bioc.biomaRt), [Biostrings](https://doi.org/doi:10.18129/B9.bioc.Biostrings), [BSgenome.Hsapiens.UCSC.hg38](https://doi.org/doi:10.18129/B9.bioc.BSgenome.Hsapiens.UCSC.hg38), [DESeq](https://doi.org/doi:10.18129/B9.bioc.DESeq), [GenomicFeatures](https://doi.org/doi:10.18129/B9.bioc.GenomicFeatures), [GenomicRanges](https://doi.org/doi:10.18129/B9.bioc.GenomicRanges), [GenomicScores](https://doi.org/doi:10.18129/B9.bioc.GenomicScores), [GenomicTools](https://cran.r-project.org/web/packages/GenomicTools/index.html), [IRanges](https://doi.org/doi:10.18129/B9.bioc.IRanges), [limma](https://doi.org/doi:10.18129/B9.bioc.limma), [maftools](https://doi.org/doi:10.18129/B9.bioc.maftools), [seqinr](https://doi.org/10.1093/nar/12.1Part1.121), [VariantAnnotation](https://doi.org/doi:10.18129/B9.bioc.VariantAnnotation).\n",
                "website": "https://docs.cloud.sdu.dk/Apps/rstudio.html",
                "public": true
            },
            "favorite": false,
            "tags": [
                "Featured",
                "Bioinformatics",
                "Data Analytics",
                "Development"
            ]
        },
        {
            "metadata": {
                "name": "siesta",
                "version": "4.1-b3-1",
                "authors": [
                    "Federica Lo Verso <federica@imada.sdu.dk>"
                ],
                "title": "SIESTA",
                "description": "SIESTA is a density-functional theory code  which allows to perform efficient electronic structure calculations  and ab initio molecular dynamics simulations of molecules  and solids with the use of a basis set of strictly-localized atomic orbitals.\n",
                "website": "https://docs.cloud.sdu.dk/Apps/siesta.html",
                "public": true
            },
            "favorite": false,
            "tags": [
                "Featured",
                "Applied Science"
            ]
        },
        {
            "metadata": {
                "name": "snakemake",
                "version": "5.10.0",
                "authors": [
                    "Johannes Koester"
                ],
                "title": "Snakemake",
                "description": "The Snakemake workflow management system is a tool to create reproducible and  scalable data analyses. Workflows are described via a human readable,  Python based language. They can be seamlessly scaled to server, cluster,  grid and cloud environments, without the need to modify the workflow definition.  Finally, Snakemake workflows can entail a description of required software,  which will be automatically deployed to any execution environment.\n",
                "website": "https://docs.cloud.sdu.dk/Apps/snakemake.html",
                "public": true
            },
            "favorite": false,
            "tags": [
                "Data Analytics",
                "Featured"
            ]
        },
        {
            "metadata": {
                "name": "spark-cluster",
                "version": "2.4.5-11",
                "authors": [
                    "Dan Sebastian Thrane <dthrane@imada.sdu.dk>",
                    "Emiliano Molinaro <molinaro@imada.sdu.dk>"
                ],
                "title": "Spark Cluster",
                "description": "Apache Spark Standalone Cluster.\n",
                "website": "https://docs.cloud.sdu.dk/Apps/spark-cluster.html",
                "public": true
            },
            "favorite": false,
            "tags": [
                "Featured",
                "Data Analytics"
            ]
        },
        {
            "metadata": {
                "name": "streamlit",
                "version": "0.64.0",
                "authors": [
                    "A. Treuille",
                    "T. Teixeira",
                    "A. Kelly"
                ],
                "title": "Streamlit",
                "description": "Streamlit is an open-source Python library that makes it easy to build beautiful custom web-apps for machine learning and data science.\n",
                "website": "https://docs.cloud.sdu.dk/Apps/streamlit.html",
                "public": true
            },
            "favorite": false,
            "tags": [
                "Data Analytics",
                "Featured"
            ]
        },
        {
            "metadata": {
                "name": "tensorflow-te",
                "version": "2.2.0",
                "authors": [
                    "Google Brain",
                    "NVIDIA Deeplearning & AI"
                ],
                "title": "TensorFlow",
                "description": "TensorFlow is an open-source software library for numerical computation using data flow graphs.  Nodes in the graph represent mathematical operations, while the graph edges represent the multidimensional data arrays (tensors) that flow between them.  This flexible architecture lets you deploy computation to one or more CPUs or GPUs in a desktop, server, or mobile device without rewriting code.\n",
                "website": "https://docs.cloud.sdu.dk/Apps/tensorflow.html",
                "public": true
            },
            "favorite": false,
            "tags": [
                "Featured",
                "Data Analytics",
                "Development"
            ]
        },
        {
            "metadata": {
                "name": "terminal-centos",
                "version": "0.4.2",
                "authors": [
                    "Emiliano Molinaro <molinaro@imada.sdu.dk>"
                ],
                "title": "Terminal CentOS",
                "description": "Web terminal window based on [ttyd](https://github.com/tsl0922/ttyd) command-line tool.\n",
                "website": "https://docs.cloud.sdu.dk/Apps/terminal.html",
                "public": true
            },
            "favorite": false,
            "tags": [
                "Featured",
                "Development"
            ]
        },
        {
            "metadata": {
                "name": "terminal-ubuntu",
                "version": "0.8.8",
                "authors": [
                    "Emiliano Molinaro <molinaro@imada.sdu.dk>"
                ],
                "title": "Terminal Ubuntu",
                "description": "Web terminal server based on [ttyd](https://github.com/tsl0922/ttyd) command-line tool.\n",
                "website": "https://docs.cloud.sdu.dk/Apps/terminal.html",
                "public": false
            },
            "favorite": false,
            "tags": [
                "Featured",
                "Development"
            ]
        },
        {
            "metadata": {
                "name": "tracer",
                "version": "May2020",
                "authors": [
                    "Greta Franzini",
                    "Emily Franzini",
                    "Kirill Bulert",
                    "Marco Büchler",
                    "Maria Moritz"
                ],
                "title": "TRACER",
                "description": "TRACER is a suite of 700 algorithms, whose features can be combined to create the optimal formula for detecting those words,  sentences and ideas that have been reused across texts.\n",
                "website": "https://docs.cloud.sdu.dk/Apps/tracer.html",
                "public": false
            },
            "favorite": false,
            "tags": [
                "Featured",
                "Data Analytics"
            ]
        },
        {
            "metadata": {
                "name": "ubuntu-xfce",
                "version": "18.04",
                "authors": [
                    "Emiliano Molinaro <molinaro@imada.sdu.dk>"
                ],
                "title": "Ubuntu Xfce",
                "description": "Ubuntu Xfce virtual desktop environment.\n",
                "website": "https://docs.cloud.sdu.dk/Apps/ubuntu.html",
                "public": false
            },
            "favorite": false,
            "tags": [
                "Featured"
            ]
        },
        {
            "metadata": {
                "name": "voila",
                "version": "0.1.23",
                "authors": [
                    "Emiliano Molinaro"
                ],
                "title": "Voilà",
                "description": "Voilà turns Jupyter notebooks into interactive standalone web applications and dashboards.\n",
                "website": "https://docs.cloud.sdu.dk/Apps/voila.html",
                "public": false
            },
            "favorite": false,
            "tags": [
                "Data Analytics",
                "Featured"
            ]
        }
    ],
};

test("Error silencer", () =>
    expect(1).toBe(1)
);
