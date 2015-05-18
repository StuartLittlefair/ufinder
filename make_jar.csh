#!/bin/csh

rm ufinder.jar
jar cmf Manifest.txt ufinder.jar UCAMFOV.xml ufinder.conf ufinder 
jarsigner -keystore myKeys ufinder.jar myself
cp ufinder.jar ~/Sites/ufinder/.


