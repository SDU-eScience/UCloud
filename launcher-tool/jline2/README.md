<!--

    Copyright (c) 2002-2016, the original author or authors.

    This software is distributable under the BSD license. See the terms of the
    BSD license in the documentation provided with this software.

    http://www.opensource.org/licenses/bsd-license.php

-->
Description
-----------

JLine is a Java library for handling console input. It is similar in functionality to [BSD editline](http://www.thrysoee.dk/editline/) and [GNU readline](http://www.gnu.org/s/readline/). People familiar with the readline/editline capabilities for modern shells (such as bash and tcsh) will find most of the command editing features of JLine to be familiar.

JLine 2.x is an evolution of [JLine 1.x](https://github.com/jline/jline) which was previously maintained at [SourceForge](http://jline.sourceforge.net/).

JLine 2.x development has come to an end, and users are encouraged to investigate the use of [JLine 3.x](https://github.com/jline/jline3) instead.

License
-------

JLine is distributed under the [BSD License](http://www.opensource.org/licenses/bsd-license.php), meaning that you are completely free to redistribute, modify, or sell it with almost no restrictions.

Documentation
-------------

* [wiki](https://github.com/jline/jline2/wiki)

Forums
------

* [jline-users](https://groups.google.com/group/jline-users)
* [jline-dev](https://groups.google.com/group/jline-dev)

Maven Usage
-----------

Use the following definition to use JLine in your maven project:

    <dependency>
      <groupId>jline</groupId>
      <artifactId>jline</artifactId>
      <version>2.14.2</version>
    </dependency>

Building
--------

### Requirements

* Maven 3+
* Java 5+

Check out and build:

    git clone git://github.com/jline/jline2.git
    cd jline2
    mvn install

