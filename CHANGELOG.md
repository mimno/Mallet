# Changelog

## [Unreleased]

## [2.1.1] - 2026-08-04

### Changed
- Reduced array writes when re-sorting topic counts during LDA sampling, improving performance especially for models with large numbers of topics ([#218](https://github.com/mimno/Mallet/pull/218), thanks @ghuls).

### Fixed
- CI: the Maven Central signing plugin was bound unconditionally, so every CI build (including release builds) failed at the sign step with no GPG key available. It's now gated behind a `release` Maven profile, activated explicitly for real releases.

## [2.1.0] - 2026-01-08

This is a serialization-breaking release due to the switch to HPPC, which affects feature alphabets. Note: this list is incomplete — it predates most of the Java 17 migration, Maven restructuring, and JShell work that also shipped in this release; see the [2.1.0 release notes](https://github.com/mimno/Mallet/releases/tag/v2.1.0) for the fuller picture.

### Added
- Nonnegative Matrix Factorization
- Word embeddings (word2vec clone)
- PagedInstanceList supports iteration correctly
- lebiathan added stratified sampling of InstanceList
- This file!

### Changed
- All merging and propagation of sampling statistics for topic modeling is now multi-threaded (if num-threads is more than 1), leading to a 5-10% speed boost.
- The primitive collections library (for example mapping String to int) has been changed from GNU trove to Carrotlabs HPPC. This change removes all GNU dependencies.
- The license has been changed from CPL to Apache.
- Use of VMID for unique identifier for serialized objects. (Breaks serialization!)
- Many small fixes suggested by ErrorProne.
- Unneeded imports removed.

### Removed
- The Matrix2 class has been removed.
- GRMM has been moved to a separate package.

### Fixed
- Te Rutherford fixed a bug where non-String instance IDs were being cast as Strings.
- The import functions (Csv2Vectors, Text2Vectors) have a case-sensitive flag, but this was not being passed to the stopword remover.

## [2.0.8] - 2016-05-03

### Changed
- The default format for document-topic proportions now prints values for all topics in order. The earlier file format (sparse listing of topic/proportion) can be restored using command line options.