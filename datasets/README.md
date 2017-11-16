# Attribute Generation

This directory contains scripts necessary to generate the inputs for all machine learning models created in this work. 
To create attributes, call `scala -J-Xmx16g -cp ../magpie/dist/Magpie.jar generate-attributes.scala`. 
This scala script will read in the raw data, compute the hull distance for each entry, generate representations, and save them into disk. 
The data is output in two different formats: \*.obj files readable by Magpie, and \*.json files that can be read by other codes (e.g., used in Python scripts).

*Note*: The raw data is too large to be made available via GitHub. Contact Logan Ward to get it.

