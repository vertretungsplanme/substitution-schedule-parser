# substitution-schedule-parser [![Build Status](https://travis-ci.org/johan12345/substitution-schedule-parser.svg?branch=master)](https://travis-ci.org/johan12345/substitution-schedule-parser)[ ![Download](https://api.bintray.com/packages/johan12345/maven/substitution-schedule-parser/images/download.svg) ](https://bintray.com/johan12345/maven/substitution-schedule-parser/_latestVersion)
Java library for parsing schools' substitution schedules. Supports multiple different systems mainly used in the German-speaking countries.

The library is used by the Android App [vertretungsplan.me](https://vertretungsplpan.me), which supports more than 100 schools. An [older version](https://github.com/johan12345/vertretungsplan) of the app is open source.

## Usage
The `sample` directory contains [a simple example](https://github.com/johan12345/substitution-schedule-parser/blob/master/sample/src/main/java/me/vertretungsplan/sample/Sample.java) that uses the library.

Builds are available through JCenter, you can find instructions how to use it with different build systems [here](https://bintray.com/johan12345/maven/substitution-schedule-parser/_latestVersion).

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

If you run into any problems building the project, you can email me at info@vertretungsplan.me.
