# Importing data

MALLET represents data as lists of "instances". All MALLET instances include a data object. An instance can also include a name and (in classification contexts) a label. For example, if the application is guessing the language of web pages, an instance might consist of a vector of word counts (data), the URL of the page (name) and the language of the page (label).

For information about the MALLET data import API, see the [data import developer's guide](import-devel).

There are two primary methods for importing data into MALLET format, first when the source data consists of many separate files, and second when the data is contained in a single file, with one instance per line.
For topic modeling, we usually want a large number of short, paragraph- to page-sized text segments. Note that this may *not* be what you think of as a "document"! In this case, the one-text-per-line file format is often a better choice than individual files.


**One file, one instance per line**: The "spreadsheet" style is the most common and useful. By default, Mallet assumes each line of the data file is in the following tab-delimited format:

    [ID]  [tag]  [text of the instance...]

After downloading and building MALLET, change to the MALLET directory and run the following command:

    bin/mallet import-file --input /data/web/data.txt --output web.mallet

In this case, the first token of each line (whitespace delimited, with optional comma) becomes the instance name, the second token becomes the label, and all additional text on the line is interpreted as a sequence of word tokens. Note that the data in this case will be a vector of feature/value pairs, such that a feature consists of a distinct word type and the value is the number of times that word occurs in the text.

**One instance per file**: The "directory" style is also available. It is sometimes preferred for collections that are already broken up into individual files. Make sure to keep track of file sizes: topic modeling works best when a "document" is about 100-500 words, so larger files may need to be broken into smaller segments. After downloading and building MALLET, change to the MALLET directory. As an example, text-only (`.txt`) versions of English web pages are in files in a directory called `sample-data/web/en` and text-only versions of German pages are in `sample-data/web/de`. Now run this command:

    bin/mallet import-dir --input sample-data/web/* --output web.mallet

MALLET will use the directory names as labels and the filenames as instance names. Note: make sure you are in the mallet directory, not the mallet/bin directory; otherwise you will get a ClassNotFoundException exception.

If your files are *not* text formatted, for example `.docx` or `.pdf`, you will need to extract text first. Mallet does not support this function.


There are many additional options to the `import-dir` and `import-file` commands. Add the `--help` option to either of these commands to get a full list. Some commonly used options to either command are:

* `--keep-sequence`. This option preserves the document as a sequence of word features, rather than a vector of word feature counts. Use this option for sequence labeling tasks. The MALLET topic modeling toolkit also requires feature sequences rather than feature vectors.
* `--preserve-case`. MALLET by default converts all word features to lowercase.
* `--remove-stopwords`. This option tells MALLET to ignore a standard list of very common English adverbs, conjunctions, pronouns and prepositions. There are `several other options](import-stoplist) related to stopword specification.
* `--token-regex`. MALLET divides documents into tokens using a regular expression. As of version 2.0.8, the default token expression is `'\p{L}[\p{L}\p{P}]+\p{L}'`, which is valid for all Unicode letters, and supports typical English non-letter patterns such as hyphens, apostrophes, and acronyms with `.` characters. Note that this expression also implicitly drops one- and two-letter words.

Other options include:

For non-English text, a good choice is --token-regex `'[\p{L}\p{M}]+'`, which means Unicode letters and marks (required for Indic scripts). MALLET currently does not support word segmentation for languages that require it, such as Chinese, Japanese, Korean, or Thai.

To include short words, use `\p{L}+` (letters only) or `'\p{L}[\p{L}\p{P}]*\p{L}|\p{L}'` (letters possibly including punctuation).

**SVMLight format**: SVMLight-style data in the format

    target feature:value feature:value ...

can be imported with

    bin/mallet import-svmlight --input train test --output train.mallet test.mallet

The `input` and `output` arguments can take multiple files that are processed together using the same Pipe. The `target` and `feature` fields can be either indices or strings. If they are indices, the indices in the Mallet alphabets and indices in the file may be different, though the data is equivalent. Targets are always interpreted as symbols/categories, so `2.718` will be treated as a unique categorical value just as if it were `red` or `tomato`.
