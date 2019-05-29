# Tool Format

A tool describes a reference to a container. Multiple applications can share
the same tool.

```yaml
---
tool: v1 # Denotes that this document is describing a tool. Tool spec is v1.

title: tool # User friendly title for the tool (currently not displayed anywhere)
name: tool-name # A unique name for the tool (used in references)
version: "1.0.0" # A version number for the tool, must be a string

container: myusername/foo # Name of the container to run. Any valid docker container string (including remote ones)
backend: UDOCKER # Can also be SINGULARITY

authors:
- A
- List
- Of
- Many
- Authors
- Dan Thrane

defaultMaxTime:
    hours: 1
    minutes: 0
    seconds: 0

description: It is a tool # (Optional) Description of tool
defaultNumberOfNodes: 1 # Optional
defaultTasksPerNode: 1 # Optional
```

# Application Format

```yaml
application: v1 # Denotes that this document is describing an application

title: My Application # User friendly title
name: app-name # A unique name for the tool (used in references)
version: "1.0.0" # A version number for the tool, must be a string
authors:
- Dan

tool:
    name: tool-name # Must match tool's name
    version: "1.0.0" # Must match tool's version

description: This is a description (can contain markdown)

tags: # A list of tags
- Toy

# The invocation describes the command to run inside of the container
# Several type of invocation arguments can be given. We will list them all 
# below:
invocation:
- application-name # We can pass a raw string. This is passed directly as an argument
- one-more-argument

# Insert a simple variable called my-variable (this must be present in 'parameters'!)
- type: var
  vars: my-variable

# Insert a more advanced variable mapping
# This example will expand to: "--file <my-variable>no-leading-space"
- type: var
  vars: my-variable
  prefixGlobal: "--file "
  suffixGlobal: "no-leading-space"

# A single block can also have multiple variables
# This example will expand to: "--files p/<var1>/s p/<var2>/s"
- type: var
  vars:
  - var1
  - var2
  prefixGlobal: "--files "
  prefixVariable: "p/"
  suffixVariable: "/s"

# A flag optionally adds an argument if the boolean variable (in this case 
# 'verbose') is true.
- type: flag
  var: verbose
  flag: -v

parameters:
  # The following variable shows all of shared properties
  verbose: # Define the variable 'verbose'
    type: bool # Denotes the type, in this case a boolean
    title: Text displayed to user
    description: Description displayed to user
    defaultValue: true # Default value of this variable
    optional: true # Variables are by default not optional
    #
    # Specific to bools
    #
    trueValue: "text-when-true" # Default: "true". Text this variable should expand to when true.
    falseValue: "text-when-false" # Default: "false". Text this variable should expand to when false.

  my_int:
    type: integer
    min: 0 # Default: null
    max: 100 # Default: null
    step: 1 # Default: null
    unitName: Percentage # Default: null

  my_float:
    type: floating_point
    min: 0 # Default: null
    max: 1 # Default: null
    step: 0.01 # Default: null
    unitName: Percentage # Default: null

  my_text:
    type: text
    
  my_file:
    type: input_file

  my_directory:
    type: input_directory

outputFileGlobs:
- "*.txt" # Capture all txt files
- "myfile.tar.gz" # Capture just a single file
- "output/file.dat" # Capture a file in a sub-folder
```