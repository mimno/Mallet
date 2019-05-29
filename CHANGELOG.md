# Changelog

## [Unreleased]

This is a serialization-breaking release due to the switch to HPPC, which affects feature alphabets.

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

## [2.0.8] - 2016-05-03

### Changed
- The default format for document-topic proportions now prints values for all topics in order. The earlier file format (sparse listing of topic/proportion) can be restored using command line options.