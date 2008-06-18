#!/bin/sh

mv $1 $1.bak9
cat doc/LICENSE-HEADER $1.bak9 > $1
