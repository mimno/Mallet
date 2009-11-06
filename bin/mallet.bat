@echo off

rem This batch file serves as a wrapper for several
rem  MALLET command line tools.

if not "%MALLET_HOME%" == "" goto gotMalletHome

echo MALLET requires an environment variable MALLET_HOME.
goto :eof

:gotMalletHome

set MALLET_CLASSPATH=%MALLET_HOME%\class;%MALLET_HOME%\lib\mallet-deps.jar
set MALLET_MEMORY=1G
set MALLET_ENCODING=UTF-8

set CMD=%1
shift

set CLASS=
if "%CMD%"=="import-dir" set CLASS=cc.mallet.classify.tui.Text2Vectors
if "%CMD%"=="import-file" set CLASS=cc.mallet.classify.tui.Csv2Vectors
if "%CMD%"=="train-classifier" set CLASS=cc.mallet.classify.tui.Vectors2Classify
if "%CMD%"=="train-topics" set CLASS=cc.mallet.topics.tui.Vectors2Topics
if "%CMD%"=="infer-topics" set CLASS=cc.mallet.topics.tui.InferTopics
if "%CMD%"=="estimate-topics" set CLASS=cc.mallet.topics.tui.EstimateTopics
if "%CMD%"=="hlda" set CLASS=cc.mallet.topics.tui.HierarchicalLDATUI
if "%CMD%"=="prune" set CLASS=cc.mallet.classify.tui.Vectors2Vectors
if "%CMD%"=="split" set CLASS=cc.mallet.classify.tui.Vectors2Vectors
if "%CMD%"=="bulk-load" set CLASS=cc.mallet.util.BulkLoader
if "%CMD%"=="run" set CLASS=%1 & shift

if not "%CLASS%" == "" goto gotClass


echo Mallet 2.0 commands: 
echo   import-dir        load the contents of a directory into mallet instances (one per file)
echo   import-file       load a single file into mallet instances (one per line)
echo   train-classifier  train a classifier from Mallet data files
echo   train-topics      train a topic model from Mallet data files
echo   infer-topics      use a trained topic model to infer topics for new documents
echo   estimate-topics   estimate the probability of new documents given a trained model
echo   hlda              train a topic model using Hierarchical LDA
echo   prune             remove features based on frequency or information gain
echo   split             divide data into testing, training, and validation portions
echo Include --help with any option for more information


goto :eof

:gotClass

set MALLET_ARGS=

:getArg

if "%1"=="" goto run
set MALLET_ARGS=%MALLET_ARGS% %1
shift
goto getArg

:run

java -Xmx%MALLET_MEMORY% -ea -Dfile.encoding=%MALLET_ENCODING% -classpath %MALLET_CLASSPATH% %CLASS% %MALLET_ARGS%

:eof