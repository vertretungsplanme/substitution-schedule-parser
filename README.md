# substitution-schedule-parser [![Build Status](https://travis-ci.org/johan12345/substitution-schedule-parser.svg?branch=master)](https://travis-ci.org/johan12345/substitution-schedule-parser)[ ![Download](https://api.bintray.com/packages/johan12345/maven/substitution-schedule-parser/images/download.svg) ](https://bintray.com/johan12345/maven/substitution-schedule-parser/_latestVersion)
Java library for parsing schools' substitution schedules. Supports multiple different systems mainly used in the German-speaking countries.

The Android App [vertretungsplan.me](https://vertretungsplan.me) is powered by this library and supports more than 100 schools. An [older version](https://github.com/johan12345/vertretungsplan) of the app is open source.

## Usage
The `sample` directory contains [a simple example](https://github.com/johan12345/substitution-schedule-parser/blob/master/sample/src/main/java/me/vertretungsplan/sample/Sample.java) that uses the library.

Builds are available through JCenter, you can find instructions how to use it with different build systems [here](https://bintray.com/johan12345/maven/substitution-schedule-parser/_latestVersion).

When you use the library in your own project or do any modifications to it, please take note of the
[License](#license) it is distributed under. I would also appreciate it if you'd tell me what exciting projects you
are using the library in, just drop me a mail at info@vertretungsplan.me!

If you run into problems using this library, you can simply
[report an issue](https://github.com/johan12345/substitution-schedule-parser/issues/new) and I will try to help you
as soon as possible.

## Supported substitution schedule software systems
Below you find the currently supported substitution schedule softwares and the corresponding values for `SubstitutionScheduleData.setApi()`:

* Untis (*not* WebUntis)
  * Monitor-Vertretungsplan ([example](http://vertretung.lornsenschule.de/schueler/subst_001.htm)) `"untis-monitor"`
  * Vertretungsplan ([example](http://www.jkg-stuttgart.de/jkgdata/vertretungsplan/sa3.htm)) `"untis-subst"`
  * Info-Stundenplan ([example](http://www.akg-bensheim.de/akgweb2011/content/Vertretung/default.htm)) `"untis-info"`
  * Info-Stundenplan without header ([example](http://www.egwerther.de/vertretungsplan/w00000.htm)) `"untis-info-headless"`
* svPlan ([example](http://www.ratsschule.de/Vplan/PH_heute.htm)) `"svplan"`
* DaVinci ([example](http://hochtaunusschule.de/hts-vertretungsplan/)) `"davinci"`
* ESchool ([example](http://eschool.topackt.com/?wp=d7406384445ce1fc9409bc90f95ccef5&go=vplan&content=x1)) `"eschool"`
* DSBmobile (with either Untis Monitor-Vertretungsplan or DaVinci inside) `"dsbmobile"`
* DSBlight (with Untis Monitor-Vertretungsplan inside) `"dsblight"`

*WebUntis* is currently not supported. It should theoretically be possible to implement a corresponding parser, but keep in mind that polling its API too often (more than once an hour) is [prohibited](http://www.grupet.at/phpBB3/viewtopic.php?f=2&t=5643#p15568).

Depending on the type of software, different options need to be supplied in `SubstitutionScheduleData.setData()`. There is no documentation about this yet.

## How to build the project
When you clone the git repository, you should directly be able to run `./gradlew sample:run` to run the sample application. All dependencies will be downloaded automatically. You can also use IDEs such as IntelliJ IDEA that support the Gradle build system.

If you run into any problems building the project, you can email me at info@vertretungsplan.me or
[report an issue](https://github.com/johan12345/substitution-schedule-parser/issues/new).

## License
This library is licensed under the [Mozilla Public License, Version 2.0](https://www.mozilla.org/en-US/MPL/2.0/).

I would like to strongly encourage people that create and use modified versions of this library, for example by fixing
bugs or adding support for a new substitution schedule software, to publish the changed source code no matter if the
resulting product is released in executable form or not (e.g. by only being run on a web server or your own computer),
even though this is only required by the terms of the license in the first case. Submitting Pull requests to
contribute your changes back to the original project is also welcome.