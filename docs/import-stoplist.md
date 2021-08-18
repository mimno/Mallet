# Data Import - Stopwords

The Mallet import commands `import-file` and `import-dir` allow you to filter a list of "stopwords" from documents before processing.
Removing high-frequency words can have a significant effect on model outputs.
There are a number of options that allow you to control which words are removed:

* `--remove-stopwords` This option invokes a standard list of English stopwords that is included in the compiled Java code. As a result, using this option does NOT read any file from disk. You cannot edit the contents of this list without modifying code and recompiling. For reference only, the contents of this list are included in the Mallet distribution in the file `stoplists/en.txt`. Editing this file has no effect, unless you also specify it with one of the following options.
* `--extra-stopwords [filename]` If you want to add additional stopwords to the standard list, you can create a file containing those words and specify it with this option. In the file, stopwords should be separated by whitespace, including any number of space and newline characters. Words can be repeated from the default stoplist. Note that this option is only active if one or both of `--remove-stopwords` or `--stoplist-file` are also used.
* `--stoplist-file [filename]` If you want complete control of the stoplist, for example if your corpus is not in English, you can use this option to specify a complete list. The format for the stoplist is the same as for `--extra-stopwords`: words are separated by spaces, tabs, or newlines. This option can be used by itself, without `--remove-stopwords`. The default, in-memory list is not used, even if `--remove-stopwords` is invoked.

## Examples

    % cat input.txt
    X	X	This is my awesome stopword test!

    % bin/mallet import-file --input input.txt --print-output 
    name: X
    target: X
    input: this(0)=1.0
    is(1)=1.0
    my(2)=1.0
    awesome(3)=1.0
    stopword(4)=1.0
    test(5)=1.0

    % bin/mallet import-file --input input.txt --print-output --remove-stopwords 
    name: X
    target: X
    input: awesome(0)=1.0
    stopword(1)=1.0
    test(2)=1.0

    % cat extra.txt
    awesome
    test

    % bin/mallet import-file --input input.txt --print-output --remove-stopwords --extra-stopwords extra.txt 
    name: X
    target: X
    input: stopword(0)=1.0

    % bin/mallet import-file --input input.txt --print-output --extra-stopwords extra.txt
    name: X
    target: X
    input: this(0)=1.0
    is(1)=1.0
    my(2)=1.0
    awesome(3)=1.0
    stopword(4)=1.0
    test(5)=1.0

    % cat stoplist.txt 
    my
    test

    % bin/mallet import-file --input input.txt --print-output --stoplist-file stoplist.txt 
    name: X
    target: X
    input: this(0)=1.0
    is(1)=1.0
    awesome(2)=1.0
    stopword(3)=1.0

    % bin/mallet import-file --input input.txt --print-output --stoplist-file stoplist.txt --extra-stopwords extra.txt 
    name: X
    target: X
    input: this(0)=1.0
    is(1)=1.0
    stopword(2)=1.0
