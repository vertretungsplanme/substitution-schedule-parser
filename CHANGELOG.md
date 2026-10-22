# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [2.0.0] - unreleased

Version 2.0.0 replaces [Joda-Time](https://www.joda.org/joda-time/) with `java.time`
throughout the public API. This is a source and binary incompatible change: code that calls
the methods listed below no longer compiles, and already compiled code fails with
`NoSuchMethodError`. `joda-time` is also no longer a dependency of the published artifact,
so consumers that used Joda-Time classes transitively through this library have to declare
it themselves. See the README for migration hints.

### Breaking
- Changed public date/time types from Joda-Time to `java.time`:
  - `SubstitutionScheduleParser.getLastChange()` (`org.joda.time.LocalDateTime` → `java.time.LocalDateTime`)
  - `SubstitutionSchedule.setLastChange`/`getLastChange` (`org.joda.time.LocalDateTime` → `java.time.LocalDateTime`)
  - `SubstitutionScheduleDay.setDate`/`getDate` (`org.joda.time.LocalDate` → `java.time.LocalDate`)
  - `SubstitutionScheduleDay.setLastChange`/`getLastChange` (`org.joda.time.LocalDateTime` → `java.time.LocalDateTime`)
  - `SubstitutionScheduleDayDiff.getDate`/`setDate` (`org.joda.time.LocalDate` → `java.time.LocalDate`)
  - `Credential.getLastCheck`/`setLastCheck` and `BaseCredential.getLastCheck`/`setLastCheck` (`org.joda.time.DateTime` → `java.time.LocalDateTime`)
- Removed the transitive `joda-time` dependency from the published POM.

### Changed
- All instants and the notion of "today" are now interpreted in `Europe/Berlin` instead of the
  JVM default time zone (`TimeZones.BERLIN`). On JVMs running in another zone, date results can
  shift by up to one day.

### Fixed
- Unparseable dates without a year no longer abort parsing with an exception; affected values
  are reported as `null` instead.

### Added
- `TimeZones.BERLIN`, the zone this library works in.
- `SubstitutionSchedule.setLastChangeFromInstant(Instant)` and
  `SubstitutionSchedule.setLastChangeFromDate(Date)` as migration helpers for consumers whose
  sources provide an instant or a `java.util.Date`.
